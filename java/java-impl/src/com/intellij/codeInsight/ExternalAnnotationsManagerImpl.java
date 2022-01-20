// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsMessageDialog;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author anna
 */
public final class ExternalAnnotationsManagerImpl extends ReadableExternalAnnotationsManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotationsManagerImpl.class);
  private static final NotificationGroup EXTERNAL_ANNOTATIONS_MESSAGES =
    NotificationGroupManager.getInstance().getNotificationGroup("External annotations");

  private final MessageBus myBus;
  private @Nullable VirtualFile myAdditionalAnnotationsRoot;

  public ExternalAnnotationsManagerImpl(@NotNull Project project) {
    super(PsiManager.getInstance(project));

    myBus = project.getMessageBus();
    MessageBusConnection connection = myBus.connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        dropAnnotationsCache();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          if (!event.isFromRefresh()) {
            continue;
          }

          String name;
          if (event instanceof VFileCreateEvent) {
            name = ((VFileCreateEvent)event).getChildName();
          }
          else {
            VirtualFile file = event.getFile();
            if (file == null) {
              continue;
            }

            name = file.getName();
          }

          if (event.isFromRefresh() && ANNOTATIONS_XML.equals(name)) {
            dropAnnotationsCache();
            notifyChangedExternally();
          }
        }
      }
    });
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        dropAnnotationsCache();
      }
    });
    RegistryValue additionalRootRegistryValue = Registry.get("java.additional.external.annotations.root.url");
    String additionalRootUrl = additionalRootRegistryValue.asString();
    if (!StringUtil.isEmptyOrSpaces(additionalRootUrl)) {
      myAdditionalAnnotationsRoot = VirtualFileManager.getInstance().refreshAndFindFileByUrl(additionalRootUrl);
    }
    additionalRootRegistryValue.addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        String url = value.asString();
        myAdditionalAnnotationsRoot =
          !StringUtil.isEmptyOrSpaces(url) ? VirtualFileManager.getInstance().refreshAndFindFileByUrl(url) : null;
        dropAnnotationsCache();
      }
    }, this);

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyDocumentListener(), this);
  }

  @Override
  public void dispose() {
  }

  private void notifyAfterAnnotationChanging(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQName, boolean successful) {
    myBus.syncPublisher(TOPIC).afterExternalAnnotationChanging(owner, annotationFQName, successful);
    myPsiManager.dropPsiCaches();
  }

  private void notifyChangedExternally() {
    myBus.syncPublisher(TOPIC).externalAnnotationsChangedExternally();
    myPsiManager.dropPsiCaches();
  }

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 PsiNameValuePair @Nullable [] value) throws CanceledConfigurationException {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    LOG.assertTrue(!application.isWriteAccessAllowed());

    final Project project = myPsiManager.getProject();
    final PsiFile containingFile = listOwner.getOriginalElement().getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    final VirtualFile containingVirtualFile = containingFile.getVirtualFile();
    LOG.assertTrue(containingVirtualFile != null);
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(containingVirtualFile);
    if (entries.isEmpty()) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    ExternalAnnotation annotation = new ExternalAnnotation(listOwner, annotationFQName, value);
    for (final OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) continue;
      VirtualFile[] roots = AnnotationOrderRootType.getFiles(entry);
      roots = filterByReadOnliness(roots);

      if (roots.length > 0) {
        chooseRootAndAnnotateExternally(roots, annotation);
      }
      else {
        if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
          notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
          return;
        }
        DumbService.getInstance(project).runWithAlternativeResolveEnabled(() -> {
          if (!setupRootAndAnnotateExternally(entry, project, annotation)) {
            throw new CanceledConfigurationException();
          }
        });
      }
      break;
    }
  }

  private void annotateExternally(@NotNull VirtualFile root, @NotNull ExternalAnnotation annotation) {
    annotateExternally(root, Collections.singletonList(annotation));
  }

  /**
   * Tries to add external annotations into given root if possible.
   * Notifies about each addition result separately.
   */
  private void annotateExternally(@NotNull VirtualFile root, @NotNull List<? extends ExternalAnnotation> annotations) {
    Project project = myPsiManager.getProject();

    Map<Optional<VirtualFile>, List<ExternalAnnotation>> annotationsByFiles = annotations.stream()
      .collect(Collectors.groupingBy(annotation -> Optional.ofNullable(getFileForAnnotations(root, annotation.getOwner(), project))
        .map(xmlFile -> xmlFile.getVirtualFile())));

    List<VirtualFile> files = StreamEx.ofKeys(annotationsByFiles).flatMap(StreamEx::of).nonNull().toList();
    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
    if (status.hasReadonlyFiles()) {
      VirtualFile[] readonlyFiles = status.getReadonlyFiles();
      annotationsByFiles.keySet()
        .removeIf(opt -> opt.filter(f -> ArrayUtil.contains(f, readonlyFiles)).isPresent());
    }

    if (annotationsByFiles.isEmpty()) return;

    WriteCommandAction.writeCommandAction(project).run(new ThrowableRunnable<>() {
      @Override
      public void run() throws RuntimeException {
        if (project.isDisposed()) return;
        if (DumbService.isDumb(project)) {
          DumbService.getInstance(project).runWhenSmart(() -> WriteCommandAction.writeCommandAction(project).run(this));
          return;
        }
        try {
          for (Map.Entry<Optional<VirtualFile>, List<ExternalAnnotation>> entry : annotationsByFiles.entrySet()) {
            VirtualFile annotationsFile = entry.getKey().orElse(null);
            if (annotationsFile == null) continue;
            List<ExternalAnnotation> fileAnnotations = entry.getValue();
            PsiFile file = PsiManager.getInstance(project).findFile(annotationsFile);
            if (file instanceof XmlFile) {
              annotateExternally((XmlFile)file, fileAnnotations);
            }
          }

          UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
            @Override
            public void undo() {
              dropAnnotationsCache();
              notifyChangedExternally();
            }

            @Override
            public void redo() {
              dropAnnotationsCache();
              notifyChangedExternally();
            }
          });
        }
        finally {
          dropAnnotationsCache();
        }
      }
    });
  }

  private void dropAnnotationsCache() {
    dropCache();
  }

  private void annotateExternally(@Nullable XmlFile annotationsFile, @NotNull List<ExternalAnnotation> annotations) {
    XmlTag rootTag = extractRootTag(annotationsFile);

    TreeMap<String, List<ExternalAnnotation>> ownerToAnnotations = StreamEx.of(annotations)
      .mapToEntry(annotation -> StringUtil.escapeXmlEntities(getExternalName(annotation.getOwner())), Function.identity())
      .distinct()
      .grouping(() -> new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder())));

    if (rootTag == null) {
      ownerToAnnotations.values().stream().flatMap(List::stream).forEach(annotation ->
                                                                           notifyAfterAnnotationChanging(annotation.getOwner(),
                                                                                                         annotation.getAnnotationFQName(),
                                                                                                         false));
      return;
    }

    List<ExternalAnnotation> savedAnnotations = new ArrayList<>();
    XmlTag startTag = null;

    for (Map.Entry<String, List<ExternalAnnotation>> entry : ownerToAnnotations.entrySet()) {
      @NonNls String ownerName = entry.getKey();
      List<ExternalAnnotation> annotationList = entry.getValue();
      for (ExternalAnnotation annotation : annotationList) {

        if (ownerName == null) {
          notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
          continue;
        }

        try {
          startTag = addAnnotation(rootTag, ownerName, annotation, startTag);
          savedAnnotations.add(annotation);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
        }
        finally {
          dropAnnotationsCache();
          markForUndo(annotation.getOwner().getContainingFile());
        }
      }
    }

    commitChanges(annotationsFile);
    savedAnnotations.forEach(annotation ->
                               notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), true));
  }

  @Override
  protected @Nullable VirtualFile getAdditionalAnnotationRoot() {
    return myAdditionalAnnotationsRoot;
  }

  @Contract("null -> null")
  private static XmlTag extractRootTag(XmlFile annotationsFile) {
    if (annotationsFile == null) {
      return null;
    }

    XmlDocument document = annotationsFile.getDocument();
    if (document == null) {
      return null;
    }

    return document.getRootTag();
  }

  private static void markForUndo(@Nullable PsiFile containingFile) {
    if (containingFile == null) {
      return;
    }

    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile != null && virtualFile.isInLocalFileSystem()) {
      UndoUtil.markPsiFileForUndo(containingFile);
    }
  }

  /**
   * Adds annotation sub tag after startTag.
   * If startTag is {@code null} searches for all sub tags of rootTag and starts from the first.
   *
   * @param rootTag    root tag to insert subtag into
   * @param ownerName  annotations owner name
   * @param annotation external annotation
   * @param startTag   start tag
   * @return added sub tag
   */
  private @NotNull XmlTag addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName,
                                        @NotNull ExternalAnnotation annotation, @Nullable XmlTag startTag) {
    if (startTag == null) {
      startTag = PsiTreeUtil.findChildOfType(rootTag, XmlTag.class);
    }

    XmlTag prevItem = null;
    XmlTag curItem = startTag;

    while (curItem != null) {
      XmlTag addedItem = addAnnotation(rootTag, ownerName, annotation, curItem, prevItem);
      if (addedItem != null) {
        return addedItem;
      }

      prevItem = curItem;
      curItem = PsiTreeUtil.getNextSiblingOfType(curItem, XmlTag.class);
    }

    return addItemTag(rootTag, prevItem, ownerName, annotation);
  }

  /**
   * Adds annotation sub tag into curItem or between prevItem and curItem.
   * Adds into curItem if curItem contains external annotations for owner.
   * Adds between curItem and prevItem if owner's external name < cur item owner external name.
   * Otherwise does nothing, returns null.
   *
   * @param rootTag    root tag to insert sub tag into
   * @param ownerName  annotation owner
   * @param annotation external annotation
   * @param curItem    current item with annotations
   * @param prevItem   previous item with annotations
   * @return added tag
   */
  private @Nullable XmlTag addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName, @NotNull ExternalAnnotation annotation,
                                         @NotNull XmlTag curItem, @Nullable XmlTag prevItem) {

    @NonNls String curItemName = curItem.getAttributeValue("name");
    if (curItemName == null) {
      curItem.delete();
      return null;
    }

    int compare = ownerName.compareTo(curItemName);

    if (compare == 0) {
      //already have external annotations for owner
      return appendItemAnnotation(curItem, annotation);
    }

    if (compare < 0) {
      return addItemTag(rootTag, prevItem, ownerName, annotation);
    }

    return null;
  }

  private @NotNull XmlTag addItemTag(@NotNull XmlTag rootTag,
                                     @Nullable XmlTag anchor,
                                     @NotNull String ownerName,
                                     @NotNull ExternalAnnotation annotation) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(myPsiManager.getProject());
    XmlTag newItemTag = elementFactory.createTagFromText(createItemTag(ownerName, annotation));

    PsiElement addedElement;
    if (anchor != null) {
      addedElement = rootTag.addAfter(newItemTag, anchor);
    }
    else {
      addedElement = rootTag.addSubTag(newItemTag, true);
    }

    if (!(addedElement instanceof XmlTag)) {
      throw new IncorrectOperationException("Failed to add annotation " + annotation + " after " + anchor);
    }

    return (XmlTag)addedElement;
  }

  /**
   * Appends annotation sub tag into itemTag. It can happen only if item tag belongs to annotation owner.
   *
   * @param itemTag    item tag with annotations
   * @param annotation external annotation
   */
  private XmlTag appendItemAnnotation(@NotNull XmlTag itemTag, @NotNull ExternalAnnotation annotation) {
    @NonNls String annotationFQName = annotation.getAnnotationFQName();
    PsiNameValuePair[] values = annotation.getValues();

    XmlElementFactory elementFactory = XmlElementFactory.getInstance(myPsiManager.getProject());

    XmlTag anchor = null;
    for (XmlTag itemAnnotation : itemTag.getSubTags()) {
      String curAnnotationName = itemAnnotation.getAttributeValue("name");
      if (curAnnotationName == null) {
        itemAnnotation.delete();
        continue;
      }

      if (annotationFQName.equals(curAnnotationName)) {
        // found tag for same annotation, replacing
        itemAnnotation.delete();
        break;
      }

      anchor = itemAnnotation;
    }

    XmlTag newAnnotationTag = elementFactory.createTagFromText(createAnnotationTag(annotationFQName, values));

    PsiElement addedElement = itemTag.addAfter(newAnnotationTag, anchor);
    if (!(addedElement instanceof XmlTag)) {
      throw new IncorrectOperationException("Failed to add annotation " + annotation + " after " + anchor);
    }

    return itemTag;
  }

  private @Nullable List<XmlFile> findExternalAnnotationsXmlFiles(@NotNull PsiModifierListOwner listOwner) {
    List<PsiFile> psiFiles = findExternalAnnotationsFiles(listOwner);
    if (psiFiles == null) {
      return null;
    }
    List<XmlFile> xmlFiles = new ArrayList<>();
    for (PsiFile psiFile : psiFiles) {
      if (psiFile instanceof XmlFile) {
        xmlFiles.add((XmlFile)psiFile);
      }
    }
    return xmlFiles;
  }

  private boolean setupRootAndAnnotateExternally(@NotNull OrderEntry entry,
                                                 @NotNull Project project,
                                                 @NotNull ExternalAnnotation annotation) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(JavaBundle.message("external.annotations.root.chooser.title", entry.getPresentableName()));
    descriptor.setDescription(JavaBundle.message("external.annotations.root.chooser.description"));
    descriptor.setForcedToUseIdeaFileChooser(true);
    final VirtualFile newRoot = FileChooser.chooseFile(descriptor, project, null);
    if (newRoot == null) {
      notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
      return false;
    }
    WriteCommandAction.writeCommandAction(project).run(() -> appendChosenAnnotationsRoot(entry, newRoot));
    annotateExternally(newRoot, annotation);
    return true;
  }

  private static @Nullable XmlFile findXmlFileInRoot(@Nullable List<? extends XmlFile> xmlFiles, @NotNull VirtualFile root) {
    if (xmlFiles != null) {
      for (XmlFile xmlFile : xmlFiles) {
        VirtualFile vf = xmlFile.getVirtualFile();
        if (vf != null) {
          if (VfsUtilCore.isAncestor(root, vf, false)) {
            return xmlFile;
          }
        }
      }
    }
    return null;
  }

  private void chooseRootAndAnnotateExternally(VirtualFile @NotNull [] roots, @NotNull ExternalAnnotation annotation) {
    if (roots.length > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(JavaBundle.message("external.annotations.roots"), roots) {
        @Override
        public void canceled() {
          notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
        }

        @Override
        public PopupStep onChosen(@NotNull VirtualFile file, final boolean finalChoice) {
          annotateExternally(file, annotation);
          return FINAL_CHOICE;
        }

        @Override
        public @NotNull String getTextFor(@NotNull VirtualFile value) {
          return value.getPresentableUrl();
        }

        @Override
        public Icon getIconFor(final VirtualFile aValue) {
          return AllIcons.Modules.Annotation;
        }
      }).showInBestPositionFor(DataManager.getInstance().getDataContext());
    }
    else {
      annotateExternally(roots[0], annotation);
    }
  }

  private static VirtualFile @NotNull [] filterByReadOnliness(VirtualFile @NotNull [] files) {
    List<VirtualFile> result = ContainerUtil.filter(files, VirtualFile::isInLocalFileSystem);
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return processExistingExternalAnnotations(listOwner, annotationFQN, annotationTag -> {
      PsiElement parent = annotationTag.getParent();
      annotationTag.delete();
      if (parent instanceof XmlTag) {
        if (((XmlTag)parent).getSubTags().length == 0) {
          parent.delete();
        }
      }
      return true;
    });
  }

  @Override
  public void elementRenamedOrMoved(@NotNull PsiModifierListOwner element, @NotNull String oldExternalName) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    try {
      final List<XmlFile> files = findExternalAnnotationsXmlFiles(element);
      if (files == null) {
        return;
      }
      for (final XmlFile file : files) {
        if (!file.isValid()) {
          continue;
        }
        final XmlDocument document = file.getDocument();
        if (document == null) {
          continue;
        }
        final XmlTag rootTag = document.getRootTag();
        if (rootTag == null) {
          continue;
        }

        for (XmlTag tag : rootTag.getSubTags()) {
          String nameValue = tag.getAttributeValue("name");
          String className = nameValue == null ? null : StringUtil.unescapeXmlEntities(nameValue);
          if (Comparing.strEqual(className, oldExternalName)) {
            WriteCommandAction.runWriteCommandAction(
              myPsiManager.getProject(), JavaBundle.message("update.external.annotations"), null, () -> {
                PsiDocumentManager.getInstance(myPsiManager.getProject()).commitAllDocuments();
                try {
                  String name = getExternalName(element);
                  tag.setAttribute("name", name == null ? null : StringUtil.escapeXmlEntities(name));
                  commitChanges(file);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }, file);
          }
        }
      }
    }
    finally {
      dropAnnotationsCache();
    }
  }


  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        PsiNameValuePair @Nullable [] value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return processExistingExternalAnnotations(listOwner, annotationFQN, annotationTag -> {
      annotationTag.replace(XmlElementFactory.getInstance(myPsiManager.getProject()).createTagFromText(
        createAnnotationTag(annotationFQN, value)));
      return true;
    });
  }

  private boolean processExistingExternalAnnotations(@NotNull PsiModifierListOwner listOwner,
                                                     @NotNull String annotationFQN,
                                                     @NotNull Processor<? super XmlTag> annotationTagProcessor) {
    try {
      final List<XmlFile> files = findExternalAnnotationsXmlFiles(listOwner);
      if (files == null) {
        notifyAfterAnnotationChanging(listOwner, annotationFQN, false);
        return false;
      }
      boolean processedAnything = false;
      for (final XmlFile file : files) {
        if (!file.isValid()) {
          continue;
        }
        final XmlDocument document = file.getDocument();
        if (document == null) {
          continue;
        }
        final XmlTag rootTag = document.getRootTag();
        if (rootTag == null) {
          continue;
        }
        final String externalName = getExternalName(listOwner);

        final List<XmlTag> tagsToProcess = new ArrayList<>();
        for (XmlTag tag : rootTag.getSubTags()) {
          String nameValue = tag.getAttributeValue("name");
          String className = nameValue == null ? null : StringUtil.unescapeXmlEntities(nameValue);
          if (!Comparing.strEqual(className, externalName)) {
            continue;
          }
          for (XmlTag annotationTag : tag.getSubTags()) {
            if (!Comparing.strEqual(annotationTag.getAttributeValue("name"), annotationFQN)) {
              continue;
            }
            tagsToProcess.add(annotationTag);
            processedAnything = true;
          }
        }
        if (tagsToProcess.isEmpty()) {
          continue;
        }
        if (ReadonlyStatusHandler.getInstance(myPsiManager.getProject())
          .ensureFilesWritable(Collections.singletonList(file.getVirtualFile())).hasReadonlyFiles()) {
          continue;
        }

        WriteCommandAction.runWriteCommandAction(myPsiManager.getProject(),
                                                 JavaBundle.message("update.external.annotations"), null, () -> {
            PsiDocumentManager.getInstance(myPsiManager.getProject()).commitAllDocuments();
            try {
              for (XmlTag annotationTag : tagsToProcess) {
                annotationTagProcessor.process(annotationTag);
              }
              commitChanges(file);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          });
      }
      notifyAfterAnnotationChanging(listOwner, annotationFQN, processedAnything);
      return processedAnything;
    }
    finally {
      dropAnnotationsCache();
    }
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element) {
    return chooseAnnotationsPlace(element, () -> AnnotationPlace.NEED_ASK_USER);
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return chooseAnnotationsPlace(element, () -> confirmNewExternalAnnotationRoot(element));
  }

  private @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element,
                                                          @NotNull Supplier<? extends AnnotationPlace> confirmNewExternalAnnotationRoot) {
    if (!element.isPhysical() && !(element.getOriginalElement() instanceof PsiCompiledElement)) {
      return AnnotationPlace.IN_CODE; //element just created
    }
    if (!element.getManager().isInProject(element)) return AnnotationPlace.EXTERNAL;
    final Project project = myPsiManager.getProject();

    //choose external place iff USE_EXTERNAL_ANNOTATIONS option is on,
    //otherwise external annotations should be read-only
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null && JavaCodeStyleSettings.getInstance(containingFile).USE_EXTERNAL_ANNOTATIONS) {
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
      if (!entries.isEmpty()) {
        for (OrderEntry entry : entries) {
          if (!(entry instanceof ModuleOrderEntry)) {
            if (AnnotationOrderRootType.getUrls(entry).length > 0) {
              return AnnotationPlace.EXTERNAL;
            }
            break;
          }
        }
      }

      return confirmNewExternalAnnotationRoot.get();
    }
    return AnnotationPlace.IN_CODE;
  }

  private static @NotNull AnnotationPlace confirmNewExternalAnnotationRoot(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    Project project = containingFile.getProject();
    final MyExternalPromptDialog dialog = ApplicationManager.getApplication().isUnitTestMode() ||
                                          ApplicationManager.getApplication().isHeadlessEnvironment()
                                          ? null
                                          : new MyExternalPromptDialog(project);
    if (dialog != null && dialog.isToBeShown()) {
      final PsiElement highlightElement = element instanceof PsiNameIdentifierOwner
                                          ? ((PsiNameIdentifierOwner)element).getNameIdentifier()
                                          : element.getNavigationElement();
      LOG.assertTrue(highlightElement != null);
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      final List<RangeHighlighter> highlighters = new ArrayList<>();
      final boolean highlight =
        editor != null && editor.getDocument() == PsiDocumentManager.getInstance(project).getDocument(containingFile);
      try {
        if (highlight) { //do not highlight for batch inspections
          final TextRange textRange = highlightElement.getTextRange();
          HighlightManager.getInstance(project).addRangeHighlight(editor,
                                                                  textRange.getStartOffset(), textRange.getEndOffset(),
                                                                  EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
          final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
          editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.CENTER);
        }

        dialog.show();
        if (dialog.getExitCode() == 2) {
          return AnnotationPlace.EXTERNAL;
        }
        else if (dialog.getExitCode() == 1) {
          return AnnotationPlace.NOWHERE;
        }
      }
      finally {
        if (highlight) {
          HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighters.get(0));
        }
      }
    }
    else if (dialog != null) {
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }
    return AnnotationPlace.IN_CODE;
  }

  private void appendChosenAnnotationsRoot(@NotNull OrderEntry entry, @NotNull VirtualFile vFile) {
    if (entry instanceof LibraryOrderEntry) {
      Library library = ((LibraryOrderEntry)entry).getLibrary();
      LOG.assertTrue(library != null);
      final Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(vFile, AnnotationOrderRootType.getInstance());
      model.commit();
    }
    else if (entry instanceof ModuleSourceOrderEntry) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(ArrayUtil.mergeArrays(extension.getExternalAnnotationsUrls(), vFile.getUrl()));
      model.commit();
    }
    else if (entry instanceof JdkOrderEntry) {
      final SdkModificator sdkModificator = ((JdkOrderEntry)entry).getJdk().getSdkModificator();
      sdkModificator.addRoot(vFile, AnnotationOrderRootType.getInstance());
      sdkModificator.commitChanges();
    }
    dropAnnotationsCache();
  }

  private static void sortItems(@NotNull XmlFile xmlFile) {
    XmlDocument document = xmlFile.getDocument();
    if (document == null) {
      return;
    }
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) {
      return;
    }

    List<XmlTag> itemTags = new ArrayList<>();
    for (XmlTag item : rootTag.getSubTags()) {
      if (item.getAttributeValue("name") != null) {
        itemTags.add(item);
      }
      else {
        item.delete();
      }
    }

    List<XmlTag> sorted = new ArrayList<>(itemTags);
    sorted.sort((item1, item2) -> {
      String externalName1 = item1.getAttributeValue("name");
      String externalName2 = item2.getAttributeValue("name");
      assert externalName1 != null && externalName2 != null; // null names were not added
      return externalName1.compareTo(externalName2);
    });
    if (!sorted.equals(itemTags)) {
      for (XmlTag item : sorted) {
        rootTag.addAfter(item, null);
        item.delete();
      }
    }
  }

  private void commitChanges(XmlFile xmlFile) {
    sortItems(xmlFile);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    Document doc = documentManager.getDocument(xmlFile);
    assert doc != null;
    documentManager.doPostponedOperationsAndUnblockDocument(doc);
    FileDocumentManager.getInstance().saveDocument(doc);
  }

  @NonNls
  private static @NotNull String createItemTag(@NotNull String ownerName, @NotNull ExternalAnnotation annotation) {
    String annotationTag = createAnnotationTag(annotation.getAnnotationFQName(), annotation.getValues());
    return String.format("<item name='%s'>%s</item>", ownerName, annotationTag);
  }

  @NonNls
  @VisibleForTesting
  public static @NotNull String createAnnotationTag(@NotNull String annotationFQName, PsiNameValuePair @Nullable [] values) {
    @NonNls String text;
    if (values != null && values.length != 0) {
      text = "  <annotation name='" + annotationFQName + "'>\n";
      text += StringUtil.join(values, pair -> "<val" +
                                              (pair.getName() != null ? " name=\"" + pair.getName() + "\"" : "") +
                                              " val=\"" + StringUtil.escapeXmlEntities(pair.getValue().getText()) + "\"/>", "    \n");
      text += "  </annotation>";
    }
    else {
      text = "  <annotation name='" + annotationFQName + "'/>\n";
    }
    return text;
  }

  private @Nullable XmlFile createAnnotationsXml(@NotNull VirtualFile root, @NonNls @NotNull String packageName) {
    return createAnnotationsXml(root, packageName, myPsiManager);
  }

  @VisibleForTesting
  public static @Nullable XmlFile createAnnotationsXml(@NotNull VirtualFile root, @NonNls @NotNull String packageName, PsiManager manager) {
    final String[] dirs = packageName.split("\\.");
    for (String dir : dirs) {
      if (dir.isEmpty()) break;
      VirtualFile subdir = root.findChild(dir);
      if (subdir == null) {
        try {
          subdir = root.createChildDirectory(null, dir);
        }
        catch (IOException e) {
          LOG.error(e);
          return null;
        }
      }
      root = subdir;
    }
    final PsiDirectory directory = manager.findDirectory(root);
    if (directory == null) return null;

    final PsiFile psiFile = directory.findFile(ANNOTATIONS_XML);
    if (psiFile instanceof XmlFile) {
      return (XmlFile)psiFile;
    }

    try {
      final PsiFileFactory factory = PsiFileFactory.getInstance(manager.getProject());
      return (XmlFile)directory.add(factory.createFileFromText(ANNOTATIONS_XML, XmlFileType.INSTANCE, "<root></root>"));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  private @Nullable XmlFile getFileForAnnotations(@NotNull VirtualFile root, @NotNull PsiModifierListOwner owner, Project project) {
    final PsiFile containingFile = owner.getOriginalElement().getContainingFile();
    String packageName = owner instanceof PsiPackage
                         ? ((PsiPackage)owner).getQualifiedName()
                         : containingFile instanceof PsiJavaFile
                           ? ((PsiJavaFile)containingFile).getPackageName() : null;
    if (packageName == null) {
      return null;
    }

    List<XmlFile> annotationsFiles = findExternalAnnotationsXmlFiles(owner);

    XmlFile fileInRoot = findXmlFileInRoot(annotationsFiles, root);
    if (fileInRoot != null) {
      return fileInRoot;
    }
    return WriteCommandAction.writeCommandAction(project).compute(() -> {
      XmlFile newAnnotationsFile = createAnnotationsXml(root, packageName);
      if (newAnnotationsFile == null) {
        return null;
      }

      Object key = owner instanceof PsiPackage ? owner : containingFile.getVirtualFile();
      if (key != null) {
        registerExternalAnnotations(key, newAnnotationsFile);
      }
      return newAnnotationsFile;
    });
  }

  @Override
  public boolean hasAnnotationRootsForFile(@NotNull VirtualFile file) {
    if (hasAnyAnnotationsRoots()) {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
      for (OrderEntry entry : fileIndex.getOrderEntriesForFile(file)) {
        if (!(entry instanceof ModuleOrderEntry) && AnnotationOrderRootType.getUrls(entry).length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected void reportXmlParseError(@NotNull VirtualFile file, @NotNull SAXParseException exception) {
    Project project = myPsiManager.getProject();
    String basePath = project.getBasePath();
    String filePath = file.getPresentableUrl();
    if (basePath != null) {
      try {
        filePath = Path.of(basePath).relativize(Path.of(file.getPath())).toString();
      }
      catch (IllegalArgumentException ignored) {
      }
    }
    Runnable openAnnotationXml =
      () -> new OpenFileDescriptor(project, file, exception.getLineNumber() - 1, exception.getColumnNumber() - 1).navigate(true);
    EXTERNAL_ANNOTATIONS_MESSAGES.createNotification(
        JavaBundle.message("external.annotations.problem.title"),
        JavaBundle.message("external.annotations.problem.parse.error", filePath, exception.getMessage()),
        NotificationType.WARNING)
      .addAction(NotificationAction.createSimple(JavaBundle.message("external.annotations.open.file"), openAnnotationXml))
      .notify(project);
  }

  @Override
  protected void duplicateError(@NotNull VirtualFile virtualFile,
                                @NotNull String externalName,
                                @NotNull String text) {
    String message = text + "; for signature: '" + externalName + "' in the file " + virtualFile.getName();
    LOG.error(message, new Throwable(), AttachmentFactory.createAttachment(virtualFile));
  }

  public static boolean areExternalAnnotationsApplicable(@NotNull PsiModifierListOwner owner) {
    if (!owner.isPhysical()) {
      PsiElement originalElement = owner.getOriginalElement();
      if (!(originalElement instanceof PsiCompiledElement)) {
        return false;
      }
    }
    if (owner instanceof PsiLocalVariable) return false;
    if (owner instanceof PsiParameter) {
      PsiElement parent = owner.getParent();
      if (parent == null || !(parent.getParent() instanceof PsiMethod)) return false;
    }
    if (!owner.getManager().isInProject(owner)) return true;
    return JavaCodeStyleSettings.getInstance(owner.getContainingFile()).USE_EXTERNAL_ANNOTATIONS;
  }

  private static class MyExternalPromptDialog extends OptionsMessageDialog {
    MyExternalPromptDialog(final Project project) {
      super(project, getMessage(), JavaBundle.message("external.annotation.prompt"), Messages.getQuestionIcon());
      init();
    }

    @Override
    protected String getOkActionName() {
      return getAddInCode();
    }

    @Override
    protected @NotNull String getCancelActionName() {
      return CommonBundle.getCancelButtonText();
    }

    @Override
    protected Action @NotNull [] createActions() {
      final Action okAction = getOKAction();
      assignMnemonic(getAddInCode(), okAction);
      final String externalName = JavaBundle.message("external.annotations.external.option");
      return new Action[]{okAction, new AbstractAction(externalName) {
        {
          assignMnemonic(externalName, this);
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
          if (canBeHidden()) {
            setToBeShown(toBeShown(), true);
          }
          close(2);
        }
      }, getCancelAction()};
    }

    @Override
    protected boolean isToBeShown() {
      return CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS;
    }

    @Override
    protected void setToBeShown(boolean value, boolean onOk) {
      CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = value;
    }

    @Override
    protected @NotNull JComponent createNorthPanel() {
      final JPanel northPanel = (JPanel)super.createNorthPanel();
      northPanel.add(new JLabel(getMessage()), BorderLayout.CENTER);
      return northPanel;
    }

    @Override
    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    private static @NlsActions.ActionText String getAddInCode() {
      return JavaBundle.message("external.annotations.in.code.option");
    }

    private static @Nls String getMessage() {
      return JavaBundle.message("external.annotations.suggestion.message");
    }
  }

  private class MyDocumentListener implements DocumentListener {

    final FileDocumentManager myFileDocumentManager = FileDocumentManager.getInstance();

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      final VirtualFile file = myFileDocumentManager.getFile(event.getDocument());
      if (file != null && ANNOTATIONS_XML.equals(file.getName()) && isUnderAnnotationRoot(file)) {
        dropAnnotationsCache();
      }
    }
  }
}
