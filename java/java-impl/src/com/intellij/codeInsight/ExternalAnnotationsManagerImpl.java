/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsMessageDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ExternalAnnotationsManagerImpl extends ExternalAnnotationsManager {
  public static final Icon ICON = IconLoader.getIcon("/modules/annotation.png");
  private static final Logger LOG = Logger.getInstance("#" + ExternalAnnotationsManagerImpl.class.getName());

  private final Map<String, List<XmlFile>> myExternalAnnotations = new ConcurrentWeakHashMap<String, List<XmlFile>>();
  private final AtomicReference<ThreeState> myHasAnyAnnotationsRoots = new AtomicReference<ThreeState>(ThreeState.UNSURE);
  private static final List<XmlFile> NULL = new ArrayList<XmlFile>();
  private final PsiManager myPsiManager;

  public ExternalAnnotationsManagerImpl(final Project project, final PsiManager psiManager) {
    myPsiManager = psiManager;
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        myExternalAnnotations.clear();
        myHasAnyAnnotationsRoots.set(ThreeState.UNSURE);
      }
    });
  }

  private ThreeState hasAnyAnnotationsRoots() {
    if (myHasAnyAnnotationsRoots.get() == ThreeState.UNSURE) {
      final Module[] modules = ModuleManager.getInstance(myPsiManager.getProject()).getModules();
      for (Module module : modules) {
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          final String[] urls = AnnotationOrderRootType.getUrls(entry);
          if (urls.length > 0) {
            myHasAnyAnnotationsRoots.set(ThreeState.YES);
            return ThreeState.YES;
          }
        }
      }
      myHasAnyAnnotationsRoots.set(ThreeState.NO);
    }
    return myHasAnyAnnotationsRoots.get();
  }

  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    return collectExternalAnnotations(listOwner).get(annotationFQN);
  }

  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final Map<String, PsiAnnotation> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : result.values().toArray(new PsiAnnotation[result.size()]);
  }

  @NotNull
  private Map<String, PsiAnnotation> collectExternalAnnotations(final PsiModifierListOwner listOwner) {
    if (hasAnyAnnotationsRoots() == ThreeState.NO) return Collections.emptyMap();
    final Map<String, PsiAnnotation> result = new HashMap<String, PsiAnnotation>();
    final List<XmlFile> files = findExternalAnnotationsFile(listOwner);
    if (files == null) {
      return Collections.emptyMap();
    }
    for (XmlFile file : files) {
      if (!file.isValid()) continue;
      final XmlDocument document = file.getDocument();
      if (document == null) continue;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) continue;
      final String externalName = getExternalName(listOwner, false);
      final String oldExternalName = getNormalizedExternalName(listOwner);
      for (final XmlTag tag : rootTag.getSubTags()) {
        final String className = tag.getAttributeValue("name");
        if (!Comparing.strEqual(className, externalName) && !Comparing.strEqual(className, oldExternalName)) {
          continue;
        }
        for (XmlTag annotationTag : tag.getSubTags()) {
          final String annotationFQN = annotationTag.getAttributeValue("name");
          final StringBuilder buf = new StringBuilder();
          for (XmlTag annotationParameter : annotationTag.getSubTags()) {
            buf.append(",");
            final String nameValue = annotationParameter.getAttributeValue("name");
            if (nameValue != null) {
              buf.append(nameValue).append("=");
            }
            buf.append(StringUtil.unescapeXml(annotationParameter.getAttributeValue("val")));
          }
          final String annotationText =
            "@" + annotationFQN + (buf.length() > 0 ? "(" + StringUtil.trimStart(buf.toString(), ",") + ")" : "");
          try {
            result.put(annotationFQN,
                       JavaPsiFacade.getInstance(listOwner.getProject()).getElementFactory().createAnnotationFromText(
                         annotationText, null));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
    return result;
  }

  private static String getExternalName(PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }


  public void annotateExternally(@NotNull final PsiModifierListOwner listOwner,
                                 @NotNull final String annotationFQName,
                                 @NotNull final PsiFile fromFile,
                                 final PsiNameValuePair[] value) {
    final Project project = listOwner.getProject();
    final PsiFile containingFile = listOwner.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return;
    }
    final String packageName = ((PsiJavaFile)containingFile).getPackageName();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
    if (entries.isEmpty()) {
      return;
    }
    for (final OrderEntry entry : entries) {
      if (entry instanceof ModuleOrderEntry) continue;
      VirtualFile[] virtualFiles = AnnotationOrderRootType.getFiles(entry);
      virtualFiles = filterByReadOnliness(virtualFiles);

      if (virtualFiles.length > 0) {
        chooseRootAndAnnotateExternally(listOwner, annotationFQName, fromFile, project, packageName, virtualFile, virtualFiles, value);
      }
      else {
        if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
          return;
        }
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            setupRootAndAnnotateExternally(entry, project, listOwner, annotationFQName, fromFile, packageName, virtualFile, value);
          }
        });
      }
      break;
    }
  }

  private void setupRootAndAnnotateExternally(final OrderEntry entry, final Project project, final PsiModifierListOwner listOwner,
                                              final String annotationFQName,
                                              final PsiFile fromFile, final String packageName, final VirtualFile virtualFile, final PsiNameValuePair[] value) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(ProjectBundle.message("external.annotations.root.chooser.title", entry.getPresentableName()));
    descriptor.setDescription(ProjectBundle.message("external.annotations.root.chooser.description"));
    final VirtualFile file = FileChooser.chooseFile(project, descriptor);
    if (file == null) {
      return;
    }
    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        appendChosenAnnotationsRoot(entry, file);
        final List<XmlFile> xmlFiles = findExternalAnnotationsFile(listOwner);
        if (xmlFiles != null) { //file already exists under appeared content root
          if (!CodeInsightUtilBase.preparePsiElementForWrite(xmlFiles.get(0))) return;
          annotateExternally(listOwner, annotationFQName, xmlFiles.get(0), fromFile, value);
        }
        else {
          final XmlFile annotationsXml = createAnnotationsXml(file, packageName);
          if (annotationsXml != null) {
            final List<XmlFile> createdFiles = new ArrayList<XmlFile>();
            createdFiles.add(annotationsXml);
            myExternalAnnotations.put(getFQN(packageName, virtualFile), createdFiles);
          }
          annotateExternally(listOwner, annotationFQName, annotationsXml, fromFile, value);
        }
      }
    }.execute();
  }

  private void chooseRootAndAnnotateExternally(final PsiModifierListOwner listOwner, final String annotationFQName, @NotNull final PsiFile fromFile,
                                               final Project project, final String packageName, final VirtualFile virtualFile,
                                               final VirtualFile[] virtualFiles, final PsiNameValuePair[] value) {
    if (virtualFiles.length > 1) {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<VirtualFile>("Annotation Roots", virtualFiles) {
        @Override
        public PopupStep onChosen(final VirtualFile file, final boolean finalChoice) {
          annotateExternally(file, listOwner, project, packageName, virtualFile, annotationFQName, fromFile, value);
          return FINAL_CHOICE;
        }

        @NotNull
        @Override
        public String getTextFor(final VirtualFile value) {
          return value.getPresentableUrl();
        }

        @Override
        public Icon getIconFor(final VirtualFile aValue) {
          return ICON;
        }
      }).showInBestPositionFor(DataManager.getInstance().getDataContext());
    }
    else {
      annotateExternally(virtualFiles[0], listOwner, project, packageName, virtualFile, annotationFQName, fromFile, value);
    }
  }

  @NotNull
  private static VirtualFile[] filterByReadOnliness(@NotNull VirtualFile[] files) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      if (file.isInLocalFileSystem()) {
        result.add(file);
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  private void annotateExternally(final VirtualFile file, final PsiModifierListOwner listOwner, final Project project,
                                  final String packageName,
                                  final VirtualFile virtualFile,
                                  final String annotationFQName,
                                  @NotNull final PsiFile fromFile,
                                  final PsiNameValuePair[] value) {
    final XmlFile[] annotationsXml = new XmlFile[1];
    List<XmlFile> xmlFiles = findExternalAnnotationsFile(listOwner);
    if (xmlFiles != null) {
      for (XmlFile xmlFile : xmlFiles) {
        final VirtualFile vXmlFile = xmlFile.getVirtualFile();
        assert vXmlFile != null;
        if (VfsUtilCore.isAncestor(file, vXmlFile, false)) {
          annotationsXml[0] = xmlFile;
          if (!CodeInsightUtilBase.preparePsiElementForWrite(xmlFile)) return;
        }
      }
    } else {
      xmlFiles = new ArrayList<XmlFile>();
    }

    final List<XmlFile> annotationFiles = new ArrayList<XmlFile>(xmlFiles);
    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        if (annotationsXml[0] == null) {
          annotationsXml[0] = createAnnotationsXml(file, packageName);
        }
        if (annotationsXml[0] != null) {
          annotationFiles.add(annotationsXml[0]);
          myExternalAnnotations.put(getFQN(packageName, virtualFile), annotationFiles);
          annotateExternally(listOwner, annotationFQName, annotationsXml[0], fromFile, value);
        }
      }
    }.execute();
  }

  public boolean deannotate(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    final List<XmlFile> files = findExternalAnnotationsFile(listOwner);
    if (files != null) {
      for (XmlFile file : files) {
        if (file.isValid()) {
          final XmlDocument document = file.getDocument();
          if (document != null) {
            final XmlTag rootTag = document.getRootTag();
            if (rootTag != null) {
              final String externalName = getExternalName(listOwner, false);
              final String oldExternalName = getNormalizedExternalName(listOwner);
              for (final XmlTag tag : rootTag.getSubTags()) {
                final String className = tag.getAttributeValue("name");
                if (Comparing.strEqual(className, externalName) || Comparing.strEqual(className, oldExternalName)) {
                  for (XmlTag annotationTag : tag.getSubTags()) {
                    if (Comparing.strEqual(annotationTag.getAttributeValue("name"), annotationFQN)) {
                      if (ReadonlyStatusHandler.getInstance(file.getProject())
                        .ensureFilesWritable(file.getVirtualFile()).hasReadonlyFiles()) {
                        return false;
                      }
                      try {
                        annotationTag.delete();
                        if (tag.getSubTags().length == 0) {
                          tag.delete();
                        }
                      }
                      catch (IncorrectOperationException e) {
                        LOG.error(e);
                      }
                      return true;
                    }
                  }
                  return false;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  public AnnotationPlace chooseAnnotationsPlace(@NotNull final PsiElement element) {
    if (!element.isPhysical()) return AnnotationPlace.IN_CODE; //element just created
    if (!element.getManager().isInProject(element)) return AnnotationPlace.EXTERNAL;
    final Project project = element.getProject();
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
    final MyExternalPromptDialog dialog = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment() ? null : new MyExternalPromptDialog(project);
    if (dialog != null && dialog.isToBeShown()) {
      final PsiElement highlightElement = element instanceof PsiNameIdentifierOwner
                                           ? ((PsiNameIdentifierOwner)element).getNameIdentifier()
                                           : element.getNavigationElement();
      LOG.assertTrue(highlightElement != null);
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
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
    return AnnotationPlace.IN_CODE;
  }

  private void appendChosenAnnotationsRoot(final OrderEntry entry, final VirtualFile vFile) {
    if (entry instanceof LibraryOrderEntry) {
      Library library = ((LibraryOrderEntry)entry).getLibrary();
      LOG.assertTrue(library != null);
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      final Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(vFile, AnnotationOrderRootType.getInstance());
      model.commit();
      rootModel.commit();
    }
    else if (entry instanceof ModuleSourceOrderEntry) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      model.setRootUrls(AnnotationOrderRootType.getInstance(), ArrayUtil.mergeArrays(
        model.getRootUrls(AnnotationOrderRootType.getInstance()), vFile.getUrl()));
      model.commit();
    }
    else if (entry instanceof JdkOrderEntry) {
      final SdkModificator sdkModificator = ((JdkOrderEntry)entry).getJdk().getSdkModificator();
      sdkModificator.addRoot(vFile, AnnotationOrderRootType.getInstance());
      sdkModificator.commitChanges();
    }
    myExternalAnnotations.clear();
  }

  private static void annotateExternally(final PsiModifierListOwner listOwner,
                                         final String annotationFQName,
                                         @Nullable final XmlFile xmlFile,
                                         @NotNull PsiFile codeUsageFile, PsiNameValuePair[] values) {
    if (xmlFile == null) return;
    try {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        final String externalName = getExternalName(listOwner, false);
        if (rootTag != null) {
          for (XmlTag tag : rootTag.getSubTags()) {
            if (Comparing.strEqual(tag.getAttributeValue("name"), externalName)) {
              for (XmlTag annTag : tag.getSubTags()) {
                if (Comparing.strEqual(annTag.getAttributeValue("name"), annotationFQName)) {
                  annTag.delete();
                  break;
                }
              }
              tag.add(XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText(createAnnotationTag(annotationFQName, values)));
              return;
            }
          }
          @NonNls String text =
            "<item name=\'" + externalName + "\'>\n";
          text += createAnnotationTag(annotationFQName, values);
          text += "</item>";
          rootTag.add(XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText(text));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      if (codeUsageFile.getVirtualFile().isInLocalFileSystem()) {
        UndoUtil.markPsiFileForUndo(codeUsageFile);
      }
    }
  }

  @NonNls
  private static String createAnnotationTag(String annotationFQName, PsiNameValuePair[] values) {
    @NonNls String text;
    if (values != null) {
      text = "  <annotation name=\'" + annotationFQName + "\'>\n";
      text += StringUtil.join(values, new Function<PsiNameValuePair, String>() {
        @Override
        public String fun(PsiNameValuePair pair) {
          if (pair.getName() != null) {
            return "<val name=\"" + pair.getName() + "\" val=\"" + StringUtil.escapeXml(pair.getValue().getText()) + "\"/>";
          }
          return "<val val=\"" + StringUtil.escapeXml(pair.getValue().getText()) + "\"/>";
        }
      }, "    \n");
      text += "  </annotation>";
    }
    else {
      text = "  <annotation name=\'" + annotationFQName + "\'/>\n";
    }
    return text;
  }

  @Nullable
  private XmlFile createAnnotationsXml(VirtualFile root, @NonNls @NotNull String packageName) {
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
      return (XmlFile)directory
        .add(PsiFileFactory.getInstance(myPsiManager.getProject()).createFileFromText(ANNOTATIONS_XML, "<root></root>"));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  private List<XmlFile> findExternalAnnotationsFile(@NotNull PsiModifierListOwner listOwner) {
    final Project project = listOwner.getProject();
    final PsiFile containingFile = listOwner.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      final String packageName = javaFile.getPackageName();
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      String fqn = getFQN(packageName, virtualFile);
      final List<XmlFile> files = myExternalAnnotations.get(fqn);
      if (files == NULL) return null;
      if (files != null) {
        for (Iterator<XmlFile> it = files.iterator(); it.hasNext();) {
          if (!it.next().isValid()) it.remove();
        }
        return files;
      }

      if (virtualFile == null) {
        return null;
      }
      final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
      for (OrderEntry entry : entries) {
        if (entry instanceof ModuleOrderEntry) {
          continue;
        }
        List<XmlFile> possibleAnnotationsXmls = null;
        final String[] externalUrls = AnnotationOrderRootType.getUrls(entry);
        for (String url : externalUrls) {
          VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
          if (root == null) continue;
          final VirtualFile ext = root.findFileByRelativePath(packageName.replace(".", "/") + "/" + ANNOTATIONS_XML);
          if (ext == null) continue;
          final PsiFile psiFile = myPsiManager.findFile(ext);
          if (!(psiFile instanceof XmlFile)) continue;
          if (possibleAnnotationsXmls == null) {
            possibleAnnotationsXmls = new ArrayList<XmlFile>();
          }
          possibleAnnotationsXmls.add((XmlFile)psiFile);
        }
        if (possibleAnnotationsXmls != null) {
          myExternalAnnotations.put(fqn, possibleAnnotationsXmls);
          return possibleAnnotationsXmls;
        }
        break;
      }
      myExternalAnnotations.put(fqn, NULL);
    }
    /*final VirtualFile virtualFile = containingFile.getVirtualFile(); //for java files only
    if (virtualFile != null) {
      final VirtualFile parent = virtualFile.getParent();
      if (parent != null) {
        final VirtualFile extFile = parent.findChild(ANNOTATIONS_XML);
        if (extFile != null) {
          return (XmlFile)psiManager.findFile(extFile);
        }
      }
    }*/

    return null;
  }

  @Nullable
  private static String getFQN(String packageName, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) return null;
    return StringUtil.getQualifiedName(packageName, virtualFile.getNameWithoutExtension());
  }

  @Nullable
  private static String getNormalizedExternalName(PsiModifierListOwner owner) {
    String externalName = getExternalName(owner, true);
    if (externalName != null) {
      if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(owner, PsiMethod.class);
        if (method != null) {
          externalName =
            externalName.substring(0, externalName.lastIndexOf(' ') + 1) + method.getParameterList().getParameterIndex((PsiParameter)owner);
        }
      }
      final int idx = externalName.indexOf('(');
      if (idx == -1) return externalName;
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        final int rightIdx = externalName.indexOf(')');
        final String[] params = externalName.substring(idx + 1, rightIdx).split(",");
        buf.append(externalName.substring(0, idx + 1));
        for (String param : params) {
          param = param.trim();
          final int spaceIdx = param.indexOf(' ');
          buf.append(spaceIdx > -1 ? param.substring(0, spaceIdx) : param).append(", ");
        }
        return StringUtil.trimEnd(buf.toString(), ", ") + externalName.substring(rightIdx);
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }
    return externalName;
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

    protected String getOkActionName() {
      return ADD_IN_CODE;
    }

    protected String getCancelActionName() {
      return CommonBundle.getCancelButtonText();
    }

    @SuppressWarnings({"NonStaticInitializer"})
    protected Action[] createActions() {
      final Action okAction = getOKAction();
      assignMnemonic(ADD_IN_CODE, okAction);
      final String externalName = ProjectBundle.message("external.annotations.external.option");
      return new Action[]{okAction, new AbstractAction(externalName) {
        {
          assignMnemonic(externalName, this);
        }

        public void actionPerformed(final ActionEvent e) {
          if (canBeHidden()) {
            setToBeShown(toBeShown(), true);
          }
          close(2);
        }
      }, getCancelAction()};
    }

    protected boolean isToBeShown() {
      return CodeStyleSettingsManager.getSettings(myProject).USE_EXTERNAL_ANNOTATIONS;
    }

    protected void setToBeShown(boolean value, boolean onOk) {
      CodeStyleSettingsManager.getSettings(myProject).USE_EXTERNAL_ANNOTATIONS = value;
    }

    protected JComponent createNorthPanel() {
      final JPanel northPanel = (JPanel)super.createNorthPanel();
      northPanel.add(new JLabel(MESSAGE), BorderLayout.CENTER);
      return northPanel;
    }

    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }
  }
}
