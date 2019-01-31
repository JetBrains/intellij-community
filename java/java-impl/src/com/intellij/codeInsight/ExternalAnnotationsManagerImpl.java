// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.OptionsMessageDialog;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author anna
 */
public class ExternalAnnotationsManagerImpl extends ReadableExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotationsManagerImpl.class);

  private final MessageBus myBus;

  public ExternalAnnotationsManagerImpl(@NotNull final Project project, final PsiManager psiManager) {
    super(psiManager);
    myBus = project.getMessageBus();
    myBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        dropCache();
      }
    });

    VirtualFileManager.getInstance().addVirtualFileListener(new MyVirtualFileListener(), project);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyDocumentListener(), project);
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
  public void annotateExternally(@NotNull final PsiModifierListOwner listOwner,
                                 @NotNull final String annotationFQName,
                                 @NotNull final PsiFile fromFile,
                                 @Nullable final PsiNameValuePair[] value) throws CanceledConfigurationException {
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
        DumbService.getInstance(project).setAlternativeResolveEnabled(true);
        try {
          if (!setupRootAndAnnotateExternally(entry, project, annotation)) {
            throw new CanceledConfigurationException();
          }
        }
        finally {
          DumbService.getInstance(project).setAlternativeResolveEnabled(false);
        }
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
  public void annotateExternally(@NotNull VirtualFile root, @NotNull List<ExternalAnnotation> annotations) {
    Project project = myPsiManager.getProject();

    Map<Optional<XmlFile>, List<ExternalAnnotation>> annotationsByFiles = annotations.stream()
      .collect(Collectors.groupingBy(annotation -> Optional.ofNullable(getFileForAnnotations(root, annotation.getOwner(), project))));

    WriteCommandAction.writeCommandAction(project).run(() -> {
      try {
        for (Map.Entry<Optional<XmlFile>, List<ExternalAnnotation>> entry : annotationsByFiles.entrySet()) {
          XmlFile annotationsFile = entry.getKey().orElse(null);
          List<ExternalAnnotation> fileAnnotations = entry.getValue();
          annotateExternally(annotationsFile, fileAnnotations);
        }

        UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
          @Override
          public void undo() {
            dropCache();
            notifyChangedExternally();
          }

          @Override
          public void redo() {
            dropCache();
            notifyChangedExternally();
          }
        });
      } finally {
        dropCache();
      }
    });
  }

  private void annotateExternally(@Nullable XmlFile annotationsFile, @NotNull List<ExternalAnnotation> annotations) {
    XmlTag rootTag = extractRootTag(annotationsFile);

    TreeMap<String, List<ExternalAnnotation>> ownerToAnnotations = StreamEx.of(annotations)
      .mapToEntry(annotation -> StringUtil.escapeXmlEntities(getExternalName(annotation.getOwner())), Function.identity())
      .distinct()
      .grouping(() -> new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder())));

    if (rootTag == null) {
      ownerToAnnotations.values().stream().flatMap(List::stream).forEach(annotation ->
                                  notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false));
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
          dropCache();
          markForUndo(annotation.getOwner().getContainingFile());
        }
      }
    }

    commitChanges(annotationsFile);
    savedAnnotations.forEach(annotation ->
                               notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), true));
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
   * @param rootTag root tag to insert subtag into
   * @param ownerName annotations owner name
   * @param annotation external annotation
   * @param startTag start tag
   * @return added sub tag
   */
  @NotNull
  private XmlTag addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName,
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
   * @param rootTag root tag to insert sub tag into
   * @param ownerName annotation owner
   * @param annotation external annotation
   * @param curItem current item with annotations
   * @param prevItem previous item with annotations
   * @return added tag
   */
  @Nullable
  private XmlTag addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName, @NotNull ExternalAnnotation annotation,
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

  @NotNull
  private XmlTag addItemTag(@NotNull XmlTag rootTag,
                            @Nullable XmlTag anchor,
                            @NotNull String ownerName,
                            @NotNull ExternalAnnotation annotation) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(myPsiManager.getProject());
    XmlTag newItemTag = elementFactory.createTagFromText(createItemTag(ownerName, annotation));

    PsiElement addedElement;
    if (anchor != null) {
      addedElement = rootTag.addAfter(newItemTag, anchor);
    } else {
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
   * @param itemTag item tag with annotations
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

  @Nullable
  private List<XmlFile> findExternalAnnotationsXmlFiles(@NotNull PsiModifierListOwner listOwner) {
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

  private boolean setupRootAndAnnotateExternally(@NotNull final OrderEntry entry,
                                                 @NotNull final Project project,
                                                 @NotNull final ExternalAnnotation annotation) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(ProjectBundle.message("external.annotations.root.chooser.title", entry.getPresentableName()));
    descriptor.setDescription(ProjectBundle.message("external.annotations.root.chooser.description"));
    final VirtualFile newRoot = FileChooser.chooseFile(descriptor, project, null);
    if (newRoot == null) {
      notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
      return false;
    }
    WriteCommandAction.writeCommandAction(project).run(() -> appendChosenAnnotationsRoot(entry, newRoot));
    annotateExternally(newRoot, annotation);
    return true;
  }

  @Nullable
  private static XmlFile findXmlFileInRoot(@Nullable List<XmlFile> xmlFiles, @NotNull VirtualFile root) {
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

  private void chooseRootAndAnnotateExternally(@NotNull VirtualFile[] roots, @NotNull ExternalAnnotation annotation) {
    if (roots.length > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<VirtualFile>("Annotation Roots", roots) {
        @Override
        public void canceled() {
          notifyAfterAnnotationChanging(annotation.getOwner(), annotation.getAnnotationFQName(), false);
        }

        @Override
        public PopupStep onChosen(@NotNull final VirtualFile file, final boolean finalChoice) {
          annotateExternally(file, annotation);
          return FINAL_CHOICE;
        }

        @NotNull
        @Override
        public String getTextFor(@NotNull final VirtualFile value) {
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

  @NotNull
  private static VirtualFile[] filterByReadOnliness(@NotNull VirtualFile[] files) {
    List<VirtualFile> result = ContainerUtil.filter(files, VirtualFile::isInLocalFileSystem);
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public boolean deannotate(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
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
            WriteCommandAction
              .runWriteCommandAction(myPsiManager.getProject(), ExternalAnnotationsManagerImpl.class.getName(), null, () -> {
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
      dropCache();
    }
  }


  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull final String annotationFQN,
                                        @Nullable final PsiNameValuePair[] value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return processExistingExternalAnnotations(listOwner, annotationFQN, annotationTag -> {
      annotationTag.replace(XmlElementFactory.getInstance(myPsiManager.getProject()).createTagFromText(
        createAnnotationTag(annotationFQN, value)));
      return true;
    });
  }

  private boolean processExistingExternalAnnotations(@NotNull final PsiModifierListOwner listOwner,
                                                     @NotNull final String annotationFQN,
                                                     @NotNull final Processor<? super XmlTag> annotationTagProcessor) {
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
        if (ReadonlyStatusHandler.getInstance(myPsiManager.getProject())
          .ensureFilesWritable(Collections.singletonList(file.getVirtualFile())).hasReadonlyFiles()) {
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

        WriteCommandAction.runWriteCommandAction(myPsiManager.getProject(), ExternalAnnotationsManagerImpl.class.getName(), null, () -> {
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
      dropCache();
    }
  }

  @Override
  @NotNull
  public AnnotationPlace chooseAnnotationsPlace(@NotNull final PsiElement element) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!element.isPhysical() && !(element.getOriginalElement() instanceof PsiCompiledElement)) return AnnotationPlace.IN_CODE; //element just created
    if (!element.getManager().isInProject(element)) return AnnotationPlace.EXTERNAL;
    final Project project = myPsiManager.getProject();

    //choose external place iff USE_EXTERNAL_ANNOTATIONS option is on,
    //otherwise external annotations should be read-only
    final PsiFile containingFile = element.getContainingFile();
    if (JavaCodeStyleSettings.getInstance(containingFile).USE_EXTERNAL_ANNOTATIONS) {
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

      final MyExternalPromptDialog dialog = ApplicationManager.getApplication().isUnitTestMode() ||
                                            ApplicationManager.getApplication().isHeadlessEnvironment() ? null : new MyExternalPromptDialog(project);
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
            final EditorColorsManager colorsManager = EditorColorsManager.getInstance();
            final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
            final TextRange textRange = highlightElement.getTextRange();
            HighlightManager.getInstance(project).addRangeHighlight(editor,
                                                                    textRange.getStartOffset(), textRange.getEndOffset(),
                                                                    attributes, true, highlighters);
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
    }
    return AnnotationPlace.IN_CODE;
  }

  private void appendChosenAnnotationsRoot(@NotNull final OrderEntry entry, @NotNull final VirtualFile vFile) {
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
    dropCache();
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
    Collections.sort(sorted, (item1, item2) -> {
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
  @NotNull
  private static String createItemTag(@NotNull String ownerName, @NotNull ExternalAnnotation annotation) {
    String annotationTag = createAnnotationTag(annotation.getAnnotationFQName(), annotation.getValues());
    return String.format("<item name=\'%s\'>%s</item>", ownerName, annotationTag);
  }

  @NonNls
  @NotNull
  @VisibleForTesting
  public static String createAnnotationTag(@NotNull String annotationFQName, @Nullable PsiNameValuePair[] values) {
    @NonNls String text;
    if (values != null && values.length != 0) {
      text = "  <annotation name=\'" + annotationFQName + "\'>\n";
      text += StringUtil.join(values, pair -> "<val" +
                                              (pair.getName() != null ? " name=\"" + pair.getName() + "\"" : "") +
                                              " val=\"" + StringUtil.escapeXmlEntities(pair.getValue().getText()) + "\"/>", "    \n");
      text += "  </annotation>";
    }
    else {
      text = "  <annotation name=\'" + annotationFQName + "\'/>\n";
    }
    return text;
  }

  @Nullable
  private XmlFile createAnnotationsXml(@NotNull VirtualFile root, @NonNls @NotNull String packageName) {
    return createAnnotationsXml(root, packageName, myPsiManager);
  }

  @Nullable
  @VisibleForTesting
  public static XmlFile createAnnotationsXml(@NotNull VirtualFile root, @NonNls @NotNull String packageName, PsiManager manager) {
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

  @Nullable
  private XmlFile getFileForAnnotations(@NotNull VirtualFile root, @NotNull PsiModifierListOwner owner, Project project) {
    return WriteCommandAction.writeCommandAction(project).compute(() -> {
      final PsiFile containingFile = owner.getOriginalElement().getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return null;
      }
      String packageName = ((PsiJavaFile)containingFile).getPackageName();

      List<XmlFile> annotationsFiles = findExternalAnnotationsXmlFiles(owner);

      XmlFile fileInRoot = findXmlFileInRoot(annotationsFiles, root);
      if (fileInRoot != null && FileModificationService.getInstance().preparePsiElementForWrite(fileInRoot)) {
        return fileInRoot;
      }

        XmlFile newAnnotationsFile = createAnnotationsXml(root, packageName);
        if (newAnnotationsFile == null) {
          return null;
        }

      registerExternalAnnotations(containingFile, newAnnotationsFile);
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
  protected void duplicateError(@NotNull PsiFile file, @NotNull String externalName, @NotNull String text) {
    String message = text + "; for signature: '" + externalName + "' in the file " + file.getName();
    LOG.error(message, new Throwable(), AttachmentFactory.createAttachment(file.getVirtualFile()));
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
    private final Project myProject;
    private static final String ADD_IN_CODE = ProjectBundle.message("external.annotations.in.code.option");
    private static final String MESSAGE = ProjectBundle.message("external.annotations.suggestion.message");

    MyExternalPromptDialog(final Project project) {
      super(project, MESSAGE, ProjectBundle.message("external.annotation.prompt"), Messages.getQuestionIcon());
      myProject = project;
      init();
    }

    @Override
    protected String getOkActionName() {
      return ADD_IN_CODE;
    }

    @Override
    @NotNull
    protected String getCancelActionName() {
      return CommonBundle.getCancelButtonText();
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      final Action okAction = getOKAction();
      assignMnemonic(ADD_IN_CODE, okAction);
      final String externalName = ProjectBundle.message("external.annotations.external.option");
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
    protected JComponent createNorthPanel() {
      final JPanel northPanel = (JPanel)super.createNorthPanel();
      northPanel.add(new JLabel(MESSAGE), BorderLayout.CENTER);
      return northPanel;
    }

    @Override
    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }
  }

  private class MyVirtualFileListener implements VirtualFileListener {
    private void processEvent(VirtualFileEvent event) {
      if (event.isFromRefresh() && ANNOTATIONS_XML.equals(event.getFileName())) {
        dropCache();
        notifyChangedExternally();
      }
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      processEvent(event);
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      processEvent(event);
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      processEvent(event);
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      processEvent(event);
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
      processEvent(event);
    }
  }

  private class MyDocumentListener implements DocumentListener {

    final FileDocumentManager myFileDocumentManager = FileDocumentManager.getInstance();

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      final VirtualFile file = myFileDocumentManager.getFile(event.getDocument());
      if (file != null && ANNOTATIONS_XML.equals(file.getName()) && isUnderAnnotationRoot(file)) {
        dropCache();
      }
    }
  }
}
