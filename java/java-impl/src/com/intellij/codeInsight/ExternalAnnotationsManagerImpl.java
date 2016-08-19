/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsMessageDialog;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author anna
 * @since 26-Jun-2007
 */
public class ExternalAnnotationsManagerImpl extends ReadableExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#" + ExternalAnnotationsManagerImpl.class.getName());

  private final MessageBus myBus;

  public ExternalAnnotationsManagerImpl(@NotNull final Project project, final PsiManager psiManager) {
    super(psiManager);
    myBus = project.getMessageBus();
    final MessageBusConnection connection = myBus.connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        dropCache();
      }
    });

    final MyVirtualFileListener fileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(fileListener);
    Disposer.register(myPsiManager.getProject(), new Disposable() {
      @Override
      public void dispose() {
        VirtualFileManager.getInstance().removeVirtualFileListener(fileListener);
      }
    });
  }

  private void notifyAfterAnnotationChanging(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQName, boolean successful) {
    myBus.syncPublisher(TOPIC).afterExternalAnnotationChanging(owner, annotationFQName, successful);
    ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incCounter();
  }

  private void notifyChangedExternally() {
    myBus.syncPublisher(TOPIC).externalAnnotationsChangedExternally();
    ((PsiModificationTrackerImpl)myPsiManager.getModificationTracker()).incCounter();
  }

  @Override
  public void annotateExternally(@NotNull final PsiModifierListOwner listOwner,
                                 @NotNull final String annotationFQName,
                                 @NotNull final PsiFile fromFile,
                                 @Nullable final PsiNameValuePair[] value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Project project = myPsiManager.getProject();
    final PsiFile containingFile = listOwner.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    final String packageName = ((PsiJavaFile)containingFile).getPackageName();
    final VirtualFile containingVirtualFile = containingFile.getVirtualFile();
    LOG.assertTrue(containingVirtualFile != null);
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(containingVirtualFile);
    if (entries.isEmpty()) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    for (final OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) continue;
      VirtualFile[] roots = AnnotationOrderRootType.getFiles(entry);
      roots = filterByReadOnliness(roots);

      if (roots.length > 0) {
        chooseRootAndAnnotateExternally(listOwner, annotationFQName, fromFile, project, packageName, roots, value);
      }
      else {
        Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
          notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
          return;
        }
        application.invokeLater(() -> DumbService.getInstance(project).withAlternativeResolveEnabled(
          () -> setupRootAndAnnotateExternally(entry, project, listOwner, annotationFQName, fromFile, packageName, value)), project.getDisposed());
      }
      break;
    }
  }

  @Nullable
  protected List<XmlFile> findExternalAnnotationsXmlFiles(@NotNull PsiModifierListOwner listOwner) {
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

  private void setupRootAndAnnotateExternally(@NotNull final OrderEntry entry,
                                              @NotNull final Project project,
                                              @NotNull final PsiModifierListOwner listOwner,
                                              @NotNull final String annotationFQName,
                                              @NotNull final PsiFile fromFile,
                                              @NotNull final String packageName,
                                              @Nullable final PsiNameValuePair[] value) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(ProjectBundle.message("external.annotations.root.chooser.title", entry.getPresentableName()));
    descriptor.setDescription(ProjectBundle.message("external.annotations.root.chooser.description"));
    final VirtualFile newRoot = FileChooser.chooseFile(descriptor, project, null);
    if (newRoot == null) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        appendChosenAnnotationsRoot(entry, newRoot);
        XmlFile xmlFileInRoot = findXmlFileInRoot(findExternalAnnotationsXmlFiles(listOwner), newRoot);
        if (xmlFileInRoot != null) { //file already exists under appeared content root
          if (!FileModificationService.getInstance().preparePsiElementForWrite(xmlFileInRoot)) {
            notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
            return;
          }
          annotateExternally(listOwner, annotationFQName, xmlFileInRoot, fromFile, value);
        }
        else {
          final XmlFile annotationsXml = createAnnotationsXml(newRoot, packageName);
          if (annotationsXml != null) {
            List<PsiFile> createdFiles = new SmartList<>(annotationsXml);
            cacheExternalAnnotations(packageName, fromFile, createdFiles);
          }
          annotateExternally(listOwner, annotationFQName, annotationsXml, fromFile, value);
        }
      }
    }.execute();
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

  private void chooseRootAndAnnotateExternally(@NotNull final PsiModifierListOwner listOwner,
                                               @NotNull final String annotationFQName,
                                               @NotNull final PsiFile fromFile,
                                               @NotNull final Project project,
                                               @NotNull final String packageName,
                                               @NotNull VirtualFile[] roots,
                                               @Nullable final PsiNameValuePair[] value) {
    if (roots.length > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<VirtualFile>("Annotation Roots", roots) {
        @Override
        public void canceled() {
          notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
        }

        @Override
        public PopupStep onChosen(@NotNull final VirtualFile file, final boolean finalChoice) {
          annotateExternally(file, listOwner, project, packageName, annotationFQName, fromFile, value);
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
      annotateExternally(roots[0], listOwner, project, packageName, annotationFQName, fromFile, value);
    }
  }

  @NotNull
  private static VirtualFile[] filterByReadOnliness(@NotNull VirtualFile[] files) {
    List<VirtualFile> result = ContainerUtil.filter(files, file -> file.isInLocalFileSystem());
    return VfsUtilCore.toVirtualFileArray(result);
  }

  private void annotateExternally(@NotNull final VirtualFile root,
                                  @NotNull final PsiModifierListOwner listOwner,
                                  @NotNull final Project project,
                                  @NotNull final String packageName,
                                  @NotNull final String annotationFQName,
                                  @NotNull final PsiFile fromFile,
                                  @Nullable final PsiNameValuePair[] value) {
    List<XmlFile> xmlFiles = findExternalAnnotationsXmlFiles(listOwner);

    final XmlFile existingXml = findXmlFileInRoot(xmlFiles, root);
    if (existingXml != null && !FileModificationService.getInstance().preparePsiElementForWrite(existingXml)) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }

    final Set<PsiFile> annotationFiles = xmlFiles == null ? new THashSet<>() : new THashSet<>(xmlFiles);

    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        if (existingXml != null) {
          annotateExternally(listOwner, annotationFQName, existingXml, fromFile, value);
        }
        else {
          XmlFile newXml = createAnnotationsXml(root, packageName);
          if (newXml == null) {
            notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
          }
          else {
            annotationFiles.add(newXml);
            cacheExternalAnnotations(packageName, fromFile, new SmartList<>(annotationFiles));
            annotateExternally(listOwner, annotationFQName, newXml, fromFile, value);
          }
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
      }
    }.execute();
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
                                                     @NotNull final Processor<XmlTag> annotationTagProcessor) {
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
          .ensureFilesWritable(file.getVirtualFile()).hasReadonlyFiles()) {
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
        final String externalName = getExternalName(listOwner, false);

        final List<XmlTag> tagsToProcess = new ArrayList<>();
        for (XmlTag tag : rootTag.getSubTags()) {
          String className = StringUtil.unescapeXml(tag.getAttributeValue("name"));
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

        CommandProcessor.getInstance().executeCommand(myPsiManager.getProject(), () -> {
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
        }, ExternalAnnotationsManagerImpl.class.getName(), null);
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
    if (!element.isPhysical()) return AnnotationPlace.IN_CODE; //element just created
    if (!element.getManager().isInProject(element)) return AnnotationPlace.EXTERNAL;
    final Project project = myPsiManager.getProject();

    //choose external place iff USE_EXTERNAL_ANNOTATIONS option is on,
    //otherwise external annotations should be read-only
    if (CodeStyleSettingsManager.getSettings(project).USE_EXTERNAL_ANNOTATIONS) {
      final PsiFile containingFile = element.getContainingFile();
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

  private void annotateExternally(@NotNull final PsiModifierListOwner listOwner,
                                  @NotNull final String annotationFQName,
                                  @Nullable final XmlFile xmlFile,
                                  @NotNull final PsiFile codeUsageFile,
                                  @Nullable final PsiNameValuePair[] values) {
    if (xmlFile == null) {
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
      return;
    }
    try {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        final String externalName = getExternalName(listOwner, false);
        if (externalName == null) {
          LOG.info("member without external name: " + listOwner);
        }
        if (rootTag != null && externalName != null) {
          XmlTag anchor = null;
          for (XmlTag item : rootTag.getSubTags()) {
            int compare = Comparing.compare(externalName, StringUtil.unescapeXml(item.getAttributeValue("name")));
            if (compare == 0) {
              anchor = null;
              for (XmlTag annotation : item.getSubTags()) {
                compare = Comparing.compare(annotationFQName, annotation.getAttributeValue("name"));
                if (compare == 0) {
                  annotation.delete();
                  break;
                }
                anchor = annotation;
              }
              XmlTag newTag = XmlElementFactory.getInstance(myPsiManager.getProject()).createTagFromText(
                createAnnotationTag(annotationFQName, values));
              item.addAfter(newTag, anchor);
              commitChanges(xmlFile);
              notifyAfterAnnotationChanging(listOwner, annotationFQName, true);
              return;
            }
            if (compare < 0) break;
            anchor = item;
          }
          @NonNls String text =
            "<item name=\'" + StringUtil.escapeXml(externalName) + "\'>\n";
          text += createAnnotationTag(annotationFQName, values);
          text += "</item>";
          rootTag.addAfter(XmlElementFactory.getInstance(myPsiManager.getProject()).createTagFromText(text), anchor);
          commitChanges(xmlFile);
          notifyAfterAnnotationChanging(listOwner, annotationFQName, true);
          return;
        }
      }
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      notifyAfterAnnotationChanging(listOwner, annotationFQName, false);
    }
    finally {
      dropCache();
      if (codeUsageFile.getVirtualFile().isInLocalFileSystem()) {
        UndoUtil.markPsiFileForUndo(codeUsageFile);
      }
    }
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
  private static String createAnnotationTag(@NotNull String annotationFQName, @Nullable PsiNameValuePair[] values) {
    @NonNls String text;
    if (values != null && values.length != 0) {
      text = "  <annotation name=\'" + annotationFQName + "\'>\n";
      text += StringUtil.join(values, pair -> "<val" +
                                          (pair.getName() != null ? " name=\"" + pair.getName() + "\"" : "") +
                                          " val=\"" + StringUtil.escapeXml(pair.getValue().getText()) + "\"/>", "    \n");
      text += "  </annotation>";
    }
    else {
      text = "  <annotation name=\'" + annotationFQName + "\'/>\n";
    }
    return text;
  }

  @Nullable
  private XmlFile createAnnotationsXml(@NotNull VirtualFile root, @NonNls @NotNull String packageName) {
    final String[] dirs = packageName.split("[\\.]");
    for (String dir : dirs) {
      if (dir.isEmpty()) break;
      VirtualFile subdir = root.findChild(dir);
      if (subdir == null) {
        try {
          subdir = root.createChildDirectory(null, dir);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      root = subdir;
    }
    final PsiDirectory directory = myPsiManager.findDirectory(root);
    if (directory == null) return null;

    final PsiFile psiFile = directory.findFile(ANNOTATIONS_XML);
    if (psiFile instanceof XmlFile) {
      return (XmlFile)psiFile;
    }

    try {
      final PsiFileFactory factory = PsiFileFactory.getInstance(myPsiManager.getProject());
      return (XmlFile)directory.add(factory.createFileFromText(ANNOTATIONS_XML, XmlFileType.INSTANCE, "<root></root>"));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  @Override
  protected void duplicateError(@NotNull PsiFile file, @NotNull String externalName, @NotNull String text) {
    String message = text + "; for signature: '" + externalName + "' in the file " + file.getVirtualFile().getPresentableUrl();
    LogMessageEx.error(LOG, message, file.getText());
  }

  private static class MyExternalPromptDialog extends OptionsMessageDialog {
    private final Project myProject;
    private static final String ADD_IN_CODE = ProjectBundle.message("external.annotations.in.code.option");
    private static final String MESSAGE = ProjectBundle.message("external.annotations.suggestion.message");

    public MyExternalPromptDialog(final Project project) {
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
    @SuppressWarnings({"NonStaticInitializer"})
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
      return CodeStyleSettingsManager.getSettings(myProject).USE_EXTERNAL_ANNOTATIONS;
    }

    @Override
    protected void setToBeShown(boolean value, boolean onOk) {
      CodeStyleSettingsManager.getSettings(myProject).USE_EXTERNAL_ANNOTATIONS = value;
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

  private class MyVirtualFileListener extends VirtualFileAdapter {
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
}
