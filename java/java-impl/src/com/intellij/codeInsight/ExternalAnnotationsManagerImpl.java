// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.CommonBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.options.LocalFolderValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsMessageDialog;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author anna
 */
public class ExternalAnnotationsManagerImpl extends ModCommandAwareExternalAnnotationsManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(ExternalAnnotationsManagerImpl.class);
  private static final NotificationGroup EXTERNAL_ANNOTATIONS_MESSAGES =
    NotificationGroupManager.getInstance().getNotificationGroup("External annotations");

  private @Nullable VirtualFile myAdditionalAnnotationsRoot;

  public ExternalAnnotationsManagerImpl(@NotNull Project project) {
    super(PsiManager.getInstance(project));

    MessageBus bus = project.getMessageBus();
    MessageBusConnection connection = bus.connect(this);

    connection.subscribe(WorkspaceModelTopics.CHANGED, new ExternalAnnotationsRootListener());
    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        if (event.isCausedByWorkspaceModelChangesOnly()) return;
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
            myPsiManager.dropPsiCaches();
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

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 PsiNameValuePair @Nullable [] value) throws CanceledConfigurationException {
    Application application = ApplicationManager.getApplication();
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(!application.isWriteAccessAllowed());
    ActionContext context = ActionContext.from(null, fromFile);

    Supplier<@NotNull ModCommand> supplier =
      () -> annotateExternallyModCommand(listOwner, annotationFQName, value);
    ModCommandExecutor.executeInteractively(context, JavaBundle.message("update.external.annotations"), null, supplier);
  }

  @Override
  public @NotNull ModCommand annotateExternallyModCommand(@NotNull PsiModifierListOwner listOwner,
                                                          @NotNull String annotationFQName,
                                                          PsiNameValuePair @Nullable [] value,
                                                          @NotNull List<@NotNull String> annotationsToRemove) {
    ActionContext context = ActionContext.from(null, listOwner.getContainingFile());
    final Project project = myPsiManager.getProject();
    final PsiFile containingFile = listOwner.getOriginalElement().getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) return ModCommand.nop();
    final VirtualFile containingVirtualFile = containingFile.getVirtualFile();
    LOG.assertTrue(containingVirtualFile != null);
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(containingVirtualFile);
    if (entries.isEmpty()) return ModCommand.nop();
    ExternalAnnotation annotation = new ExternalAnnotation(listOwner, annotationFQName, value);
    OrderEntry entry = ContainerUtil.find(entries, e -> !(e instanceof ModuleOrderEntry));
    if (entry == null) return ModCommand.nop();
    List<AnnotateForRootCommand> commands = StreamEx.of(AnnotationOrderRootType.getFiles(entry))
      .filter(VirtualFile::isInLocalFileSystem)
      .distinct()
      .map(root -> new AnnotateForRootCommand(this, root, annotation, annotationsToRemove))
      .toList();

    if (!commands.isEmpty()) {
      return ModCommand.chooseAction(JavaBundle.message("external.annotations.roots"), commands);
    }
    return setupRootAndAnnotateExternally(containingFile, annotation, context, annotationsToRemove);
  }

  private @NotNull ModCommand getAddAnnotationCommand(@NotNull VirtualFile root, @NotNull ExternalAnnotation annotation,
                                                      @NotNull ActionContext context, @NotNull List<String> annotationsToRemove) {
    Project project = myPsiManager.getProject();

    PsiManager psiManager = PsiManager.getInstance(project);
    PsiDirectory rootDir = psiManager.findDirectory(root);
    if (rootDir == null) return ModCommand.nop();
    ModCommand command = ModCommand.psiUpdate(context, updater -> {
      PsiDirectory writableRoot = updater.getWritable(rootDir);
      PsiModifierListOwner owner = annotation.owner();
      final PsiFile containingFile = owner.getOriginalElement().getContainingFile();
      String packageName = owner instanceof PsiPackage psiPackage
                           ? psiPackage.getQualifiedName()
                           : containingFile instanceof PsiJavaFile javaFile
                             ? javaFile.getPackageName() : null;
      if (packageName == null) {
        updater.cancel(JavaBundle.message("external.annotations.no.package"));
        return;
      }
      XmlFile file = updater.getWritable(createAnnotationsXml(updater, writableRoot, packageName));
      annotateExternally(file, annotation, annotationsToRemove);
    });
    return command;
  }

  private void dropAnnotationsCache() {
    dropCache();
  }

  private void annotateExternally(@NotNull XmlFile annotationsFile, 
                                  @NotNull ExternalAnnotation annotation,
                                  @NotNull List<String> annotationsToRemove) {
    String externalName = getExternalName(annotation.owner());
    if (externalName == null) return;
    externalName = StringUtil.escapeXmlEntities(externalName);
    XmlTag rootTag = extractRootTag(annotationsFile);
    if (rootTag == null) return;

    addAnnotation(rootTag, externalName, annotation, annotationsToRemove);
    commitChanges(annotationsFile);
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

  /**
   * Adds annotation sub tag after startTag.
   * If startTag is {@code null} searches for all sub tags of rootTag and starts from the first.
   *
   * @param rootTag             root tag to insert subtag into
   * @param ownerName           annotations owner name
   * @param annotation          external annotation
   * @param annotationsToRemove list of annotation FQNs that should be removed, if present
   */
  private void addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName, @NotNull ExternalAnnotation annotation,
                             @NotNull List<String> annotationsToRemove) {
    XmlTag startTag = PsiTreeUtil.findChildOfType(rootTag, XmlTag.class);

    XmlTag prevItem = null;
    XmlTag curItem = startTag;

    while (curItem != null) {
      XmlTag addedItem = addAnnotation(rootTag, ownerName, annotation, annotationsToRemove, curItem, prevItem);
      if (addedItem != null) {
        return;
      }

      prevItem = curItem;
      curItem = PsiTreeUtil.getNextSiblingOfType(curItem, XmlTag.class);
    }

    addItemTag(rootTag, prevItem, ownerName, annotation);
  }

  /**
   * Adds annotation sub tag into curItem or between prevItem and curItem.
   * Adds into curItem if curItem contains external annotations for the owner.
   * Adds between curItem and prevItem if owner's external name < cur item owner external name.
   * Otherwise, does nothing, returns null.
   *
   * @param rootTag             root tag to insert sub tag into
   * @param ownerName           annotation owner
   * @param annotation          external annotation
   * @param annotationsToRemove list of annotation FQNs that should be removed, if present
   * @param curItem             current item with annotations
   * @param prevItem            previous item with annotations
   * @return added tag
   */
  private @Nullable XmlTag addAnnotation(@NotNull XmlTag rootTag, @NotNull String ownerName, @NotNull ExternalAnnotation annotation,
                                         @NotNull List<String> annotationsToRemove, @NotNull XmlTag curItem, @Nullable XmlTag prevItem) {

    @NonNls String curItemName = curItem.getAttributeValue("name");
    if (curItemName == null) {
      curItem.delete();
      return null;
    }

    int compare = ownerName.compareTo(curItemName);

    if (compare == 0) {
      //already have external annotations for the owner
      return appendItemAnnotation(curItem, annotation, annotationsToRemove);
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
    CodeStyleManager.getInstance(myPsiManager.getProject()).reformat(addedElement);

    return (XmlTag)addedElement;
  }

  /**
   * Appends annotation sub tag into itemTag. It can happen only if the item tag belongs to an annotation owner.
   *
   * @param itemTag             item tag with annotations
   * @param annotation          external annotation
   * @param annotationsToRemove list of annotation FQNs that should be removed, if present
   */
  private XmlTag appendItemAnnotation(@NotNull XmlTag itemTag, @NotNull ExternalAnnotation annotation,
                                      @NotNull List<String> annotationsToRemove) {
    @NonNls String annotationFQName = annotation.annotationFQName();
    PsiNameValuePair[] values = annotation.getValues();

    XmlElementFactory elementFactory = XmlElementFactory.getInstance(myPsiManager.getProject());

    XmlTag anchor = null;
    for (XmlTag itemAnnotation : itemTag.getSubTags()) {
      String curAnnotationName = itemAnnotation.getAttributeValue("name");
      if (curAnnotationName == null || annotationsToRemove.contains(curAnnotationName)) {
        itemAnnotation.delete();
      }
    }
    for (XmlTag itemAnnotation : itemTag.getSubTags()) {
      String curAnnotationName = itemAnnotation.getAttributeValue("name");
      if (annotationFQName.equals(curAnnotationName)) {
        // found tag for the same annotation, replacing
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
    CodeStyleManager.getInstance(myPsiManager.getProject()).reformat(addedElement);

    return itemTag;
  }

  private @NotNull ModCommand setupRootAndAnnotateExternally(@NotNull PsiFile containingFile,
                                                             @NotNull ExternalAnnotation annotation,
                                                             @NotNull ActionContext context,
                                                             @NotNull List<@NotNull String> annotationsToRemove) {
    String path = containingFile.getProject().getBasePath();
    class RootConfig implements OptionContainer {
      @SuppressWarnings("FieldMayBeFinal") String myExternalAnnotationsRoot = path;

      @Override
      public @NotNull OptPane getOptionsPane() {
        String title = JavaBundle.message("external.annotations.root.chooser");
        return OptPane.pane(OptPane.string("myExternalAnnotationsRoot", title, new LocalFolderValidator(title)));
      }
    }

    return new ModEditOptions<>(JavaBundle.message("external.annotations.root.chooser"), RootConfig::new, false,
                                config -> {
                                  String newRoot = config.myExternalAnnotationsRoot;
                                  VirtualFile newRootFile;
                                  try {
                                    newRootFile = VfsUtil.findFile(Path.of(newRoot), true);
                                  }
                                  catch (InvalidPathException e) {
                                    return ModCommand.error(e.getMessage());
                                  }
                                  if (newRootFile == null) {
                                    return ModCommand.error(JavaBundle.message("external.annotations.root.chooser.error", newRoot));
                                  }
                                  return ModCommand.updateOptionList(containingFile, "OrderEntryConfiguration.externalAnnotations",
                                                                     list -> list.add(newRootFile.getUrl()))
                                    .andThen(getAddAnnotationCommand(newRootFile, annotation, context, annotationsToRemove));
                                });
  }

  private record AnnotateForRootCommand(@NotNull ExternalAnnotationsManagerImpl manager,
                                        @NotNull VirtualFile root,
                                        @NotNull ExternalAnnotation annotation, 
                                        @NotNull List<@NotNull String> annotationsToRemove) implements ModCommandAction {
    @Override
    public @NotNull Presentation getPresentation(@NotNull ActionContext context) {
      return Presentation.of(root.getPresentableUrl()).withIcon(AllIcons.Modules.Annotation);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      return manager.getAddAnnotationCommand(root, annotation, context, annotationsToRemove);
    }

    @Override
    public @NotNull String getFamilyName() {
      return root.getPresentableUrl();
    }
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    ThreadingAssertions.assertEventDispatchThread();
    ModCommand command = deannotateModCommand(List.of(listOwner), List.of(annotationFQN));
    if (command.isEmpty()) return false;
    ModCommandExecutor.executeInteractively(ActionContext.from(null, listOwner.getContainingFile()),
                                            JavaBundle.message("update.external.annotations"),
                                            null,
                                            () -> command);
    return true;
  }

  @Override
  public void elementRenamedOrMoved(@NotNull PsiModifierListOwner element, @NotNull String oldExternalName) {
    ThreadingAssertions.assertEventDispatchThread();
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
      dropCache();
    }
  }


  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        PsiNameValuePair @Nullable [] value) {
    ThreadingAssertions.assertEventDispatchThread();
    ModCommand command = editExternalAnnotationModCommand(listOwner, annotationFQN, value);
    if (command.isEmpty()) return false;
    ModCommandExecutor.executeInteractively(ActionContext.from(null, listOwner.getContainingFile()),
                                            JavaBundle.message("update.external.annotations"),
                                            null,
                                            () -> command);
    return true;
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element) {
    return chooseAnnotationsPlace(element, () -> AnnotationPlace.NEED_ASK_USER);
  }

  @Override
  public @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    ThreadingAssertions.assertEventDispatchThread();
    return chooseAnnotationsPlace(element, () -> confirmNewExternalAnnotationRoot(element));
  }

  private @NotNull AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element,
                                                          @NotNull Supplier<AnnotationPlace> confirmNewExternalAnnotationRoot) {
    if (!element.isPhysical() && !(element.getOriginalElement() instanceof PsiCompiledElement)) {
      return AnnotationPlace.IN_CODE; //element just created
    }
    if (element instanceof PsiLocalVariable) {
      return AnnotationPlace.IN_CODE;
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
            if (AnnotationOrderRootType.hasUrls(entry)) {
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
      final PsiElement highlightElement = element instanceof PsiNameIdentifierOwner owner
                                          ? owner.getNameIdentifier()
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

  private void commitChanges(XmlFile xmlFile) {
    sortItems(xmlFile);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myPsiManager.getProject());
    Document doc = xmlFile.getFileDocument();
    documentManager.doPostponedOperationsAndUnblockDocument(doc);
  }

  private static @NonNls @NotNull String createItemTag(@NotNull String ownerName, @NotNull ExternalAnnotation annotation) {
    String annotationTag = createAnnotationTag(annotation.annotationFQName(), annotation.getValues());
    return String.format("<item name='%s'>%s</item>", ownerName, annotationTag);
  }

  @VisibleForTesting
  public static @NotNull XmlFile createAnnotationsXml(@Nullable ModPsiUpdater updater, @NotNull PsiDirectory root, @NonNls @NotNull String packageName) {
    final String[] dirs = packageName.split("\\.");
    for (String dir : dirs) {
      if (dir.isEmpty()) break;
      PsiDirectory subdir = root.findSubdirectory(dir);
      if (subdir == null) {
        subdir = root.createSubdirectory(dir);
      }
      else if (updater != null) {
        subdir = updater.getWritable(subdir);
      }
      root = subdir;
    }
    final PsiDirectory directory = root;

    final PsiFile psiFile = directory.findFile(ANNOTATIONS_XML);
    if (psiFile instanceof XmlFile xmlFile) {
      return xmlFile;
    }

    final PsiFileFactory factory = PsiFileFactory.getInstance(root.getProject());
    return (XmlFile)directory.add(factory.createFileFromText(ANNOTATIONS_XML, XmlFileType.INSTANCE, "<root></root>"));
  }

  @Override
  public boolean hasAnnotationRootsForFile(@NotNull VirtualFile file) {
    if (hasAnyAnnotationsRoots()) {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
      for (OrderEntry entry : fileIndex.getOrderEntriesForFile(file)) {
        if (!(entry instanceof ModuleOrderEntry) && AnnotationOrderRootType.hasUrls(entry)) {
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
    LOG.error(message, new Throwable(), CoreAttachmentFactory.createAttachment(virtualFile));
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
    if (owner instanceof PsiRecordComponent) return false;
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
      return  CommonBundle.getCancelButtonText();
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
      return CodeStyle.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS;
    }

    @Override
    protected void setToBeShown(boolean value, boolean onOk) {
      CodeStyle.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = value;
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

  private class ExternalAnnotationsRootListener implements WorkspaceModelChangeListener {
    @Override
    public void changed(@NotNull VersionedStorageChange event) {
      if (hasAnnotationRootInChanges(event, LibraryEntity.class, ExternalAnnotationsRootListener::hasAnnotationRoot) ||
          hasAnnotationRootInChanges(event, ModuleCustomImlDataEntity.class, ExternalAnnotationsRootListener::hasAnnotationRoot)) {
        dropAnnotationsCache();
      }
    }

    private static <T extends WorkspaceEntity> boolean hasAnnotationRootInChanges(@NotNull VersionedStorageChange event,
                                                                                  @NotNull Class<T> entityClass,
                                                                                  @NotNull Predicate<T> hasAnnotationRoot) {
      for (EntityChange<T> change : event.getChanges(entityClass)) {
        T newEntity = change.getNewEntity();
        T oldEntity = change.getOldEntity();
        if (newEntity != null && hasAnnotationRoot.test(newEntity) || oldEntity != null && hasAnnotationRoot.test(oldEntity)) {
          return true;
        }
      }
      return false;
    }

    private static boolean hasAnnotationRoot(LibraryEntity e) {
      return ContainerUtil.exists(e.getRoots(), root -> AnnotationOrderRootType.ANNOTATIONS_ID.equals(root.getType().getName()));
    }

    private static boolean hasAnnotationRoot(ModuleCustomImlDataEntity e) {
      String tagCustomData = e.getRootManagerTagCustomData();
      if (tagCustomData != null) {
        try {
          Element element = JDOMUtil.load(tagCustomData);
          Element child = element.getChild("annotation-paths");
          if (child != null && !child.getChildren("root").isEmpty()) {
            return true;
          }
        }
        catch (Throwable ex) {
          return false;
        }
      }
      return false;
    }
  }
}
