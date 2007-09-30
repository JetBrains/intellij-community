/*
 * User: anna
 * Date: 26-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.OptionsMessageDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExternalAnnotationsManagerImpl extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#" + ExternalAnnotationsManagerImpl.class.getName());

  @NonNls private static final String EXTERNAL_ANNOTATIONS_PROPERTY = "ExternalAnnotations";

  private Map<VirtualFile, XmlFile> myExternalAnotations = new WeakHashMap<VirtualFile, XmlFile>();
  private PsiManager myPsiManager;

  public ExternalAnnotationsManagerImpl(final Project project, final PsiManager psiManager) {
    myPsiManager = psiManager;
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {}

      public void rootsChanged(ModuleRootEvent event) {
        myExternalAnotations.clear();
      }
    });
  }

  @Nullable
  public PsiAnnotation findExternalAnnotation(final PsiModifierListOwner listOwner, final String annotationFQN) {
    return collectExternalAnnotations(listOwner).get(annotationFQN);
  }

  @Nullable
  public PsiAnnotation[] findExternalAnnotations(final PsiModifierListOwner listOwner) {
    final Map<String, PsiAnnotation> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : result.values().toArray(new PsiAnnotation[result.size()]);
  }

  private Map<String, PsiAnnotation> collectExternalAnnotations(final PsiModifierListOwner listOwner) {
    final Map<String, PsiAnnotation> result = new HashMap<String, PsiAnnotation>();
    final XmlFile xmlFile = findExternalAnnotationsFile(listOwner);
    if (xmlFile != null) {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          final String externalName = getNormalizedExternalName(listOwner);
          for (final XmlTag tag : rootTag.getSubTags()) {
            final String className = tag.getAttributeValue("name");
            if (Comparing.strEqual(className, externalName)) {
              for (XmlTag annotationTag : tag.getSubTags()) {
                final String annotationFQN = annotationTag.getAttributeValue("name");
                final StringBuilder buf = new StringBuilder();
                for (XmlTag annotationaParameter : annotationTag.getSubTags()) {
                  buf.append(",").append(annotationaParameter.getAttributeValue("name")).append("=")
                    .append(annotationaParameter.getAttributeValue("value"));
                }
                final String annotationText =
                  "@" + annotationFQN + (buf.length() > 0 ? "(" + StringUtil.trimStart(buf.toString(), ",") + ")" : "");
                try {
                  result.put(annotationFQN, listOwner.getManager().getElementFactory().createAnnotationFromText(annotationText, null));
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }
      }
    }
    return result;
  }


  public void annotateExternally(final PsiModifierListOwner listOwner, final String annotationFQName) {
    final Project project = listOwner.getProject();
    XmlFile xmlFile = findExternalAnnotationsFile(listOwner);
    if (xmlFile != null) {
      if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(xmlFile.getVirtualFile()).hasReadonlyFiles()) return;
      annotateExternally(listOwner, annotationFQName, xmlFile);
    }
    else {
      final PsiFile containingFile = listOwner.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        final String packageName = ((PsiJavaFile)containingFile).getPackageName();
        final VirtualFile virtualFile = containingFile.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
        if (!entries.isEmpty()) {
          for (final OrderEntry entry : entries) {
            if (!(entry instanceof ModuleOrderEntry)) {
              final VirtualFile[] virtualFiles = entry.getFiles(OrderRootType.ANNOTATIONS);
              if (virtualFiles.length > 0) {
                final XmlFile annotationsXml = createAnnotationsXml(virtualFiles[0], packageName);
                myExternalAnotations.put(virtualFile, annotationsXml);
                annotateExternally(listOwner, annotationFQName, annotationsXml);
              }
              else {
                if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;
                SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
                    descriptor.setTitle(ProjectBundle.message("external.annotations.root.chooser.title", entry.getPresentableName()));
                    descriptor.setDescription(ProjectBundle.message("external.annotations.root.chooser.description"));
                    final VirtualFile[] files = FileChooser.chooseFiles(project, descriptor);
                    if (files.length > 0) {
                      new WriteCommandAction(project, null) {
                        protected void run(final Result result) throws Throwable {
                          if (files[0] != null) {
                            appendChosenAnnotationsRoot(entry, files[0]);
                            final XmlFile xmlFile = findExternalAnnotationsFile(listOwner);
                            if (xmlFile != null) { //file already exists under appeared content root
                              if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(xmlFile.getVirtualFile()).hasReadonlyFiles()) return;
                              annotateExternally(listOwner, annotationFQName, xmlFile);
                            } else {
                              final XmlFile annotationsXml = createAnnotationsXml(files[0], packageName);
                              myExternalAnotations.put(virtualFile, annotationsXml);
                              annotateExternally(listOwner, annotationFQName, annotationsXml);
                            }
                          }
                        }
                      }.execute();
                    }
                  }
                });
              }
              break;
            }
          }
        }
      }
    }
  }

  public boolean deannotate(final PsiModifierListOwner listOwner, final String annotationFQN) {
    final XmlFile xmlFile = findExternalAnnotationsFile(listOwner);
    if (xmlFile != null) {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          final String externalName = getNormalizedExternalName(listOwner);
          for (final XmlTag tag : rootTag.getSubTags()) {
            final String className = tag.getAttributeValue("name");
            if (Comparing.strEqual(className, externalName)) {
              for (XmlTag annotationTag : tag.getSubTags()) {
                if (Comparing.strEqual(annotationTag.getAttributeValue("name"), annotationFQN)) {
                  if (ReadonlyStatusHandler.getInstance(xmlFile.getProject())
                    .ensureFilesWritable(xmlFile.getVirtualFile()).hasReadonlyFiles()) return false;
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
    return false; 
  }

  public AnnotationPlace chooseAnnotationsPlace(@NotNull final PsiElement element) {
    if (!element.isPhysical()) return AnnotationPlace.IN_CODE; //element just created
    if (!element.getManager().isInProject(element)) return AnnotationPlace.EXTERNAL;
    final Project project = element.getProject();
    final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
    if (!entries.isEmpty()) {
      for (OrderEntry entry : entries) {
        if (!(entry instanceof ModuleOrderEntry)) {
          if (entry.getUrls(OrderRootType.ANNOTATIONS).length > 0) {
            return AnnotationPlace.EXTERNAL;
          }
          break;
        }
      }
    }
    final MyExternalPromptDialog dialog = new MyExternalPromptDialog(project);
    if (dialog.isToBeShown()) {
      dialog.show();
      if (dialog.getExitCode() == 2) {
        return AnnotationPlace.EXTERNAL;
      } else if (dialog.getExitCode() == 1) {
        return AnnotationPlace.NOWHERE;
      }
    }
    return AnnotationPlace.IN_CODE;
  }

  private void appendChosenAnnotationsRoot(final OrderEntry entry, final VirtualFile vFile) {
    if (entry instanceof LibraryOrderEntry) {
      Library library = ((LibraryOrderEntry)entry).getLibrary();
      LOG.assertTrue(library != null);
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      final Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(vFile, OrderRootType.ANNOTATIONS);
      model.commit();
      rootModel.commit();
    }
    else if (entry instanceof ModuleSourceOrderEntry) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(entry.getOwnerModule()).getModifiableModel();
      model.setAnnotationUrls(ArrayUtil.mergeArrays(model.getAnnotationUrls(), new String[]{vFile.getUrl()}, String.class));
      model.commit();
    }
    else if (entry instanceof JdkOrderEntry) {
      final SdkModificator sdkModificator = ((JdkOrderEntry)entry).getJdk().getSdkModificator();
      sdkModificator.addRoot(vFile, ProjectRootType.ANNOTATIONS);
      sdkModificator.commitChanges();
    }
    myExternalAnotations.clear();
  }

  private static void annotateExternally(final PsiModifierListOwner listOwner,
                                         final String annotationFQName,
                                         @Nullable final XmlFile xmlFile) {
    if (xmlFile == null) return;
    try {
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        final String externalName = getNormalizedExternalName(listOwner);
        if (rootTag != null) {
          for (XmlTag tag : rootTag.getSubTags()) {
            if (Comparing.strEqual(tag.getAttributeValue("name"), externalName)) {
              tag.add(xmlFile.getManager().getElementFactory().createTagFromText("<annotation name=\'" + annotationFQName + "\'/>"));
              return;
            }
          }
          @NonNls final String text =
            "<item name=\'" + externalName + "\'>\n" + "  <annotation name=\'" + annotationFQName + "\'/>\n" + "</item>";
          rootTag.add(xmlFile.getManager().getElementFactory().createTagFromText(text));
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private XmlFile createAnnotationsXml(VirtualFile root, String packageName) {
    final String[] dirs = packageName.split("[\\.]");
    for (String dir : dirs) {
      if (dir.length() == 0) break;
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
      return (XmlFile)directory.add(myPsiManager.getElementFactory().createFileFromText(ANNOTATIONS_XML, "<root></root>"));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  private synchronized XmlFile findExternalAnnotationsFile(PsiModifierListOwner listOwner) {
    final Project project = listOwner.getProject();
    final PsiFile containingFile = listOwner.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (myExternalAnotations.containsKey(virtualFile)) {
        return myExternalAnotations.get(virtualFile);
      }
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      final String packageName = javaFile.getPackageName();
      final PsiPackage psiPackage = myPsiManager.findPackage(packageName);
      if (psiPackage != null) {
        final Module module = ModuleUtil.findModuleForPsiElement(javaFile);
        final PsiDirectory[] dirsWithExternalAnnotations = module != null
                                                           ? psiPackage.getDirectories(GlobalSearchScope.moduleScope(module)) : psiPackage.getDirectories();
        for (final PsiDirectory directory : dirsWithExternalAnnotations) {
          final PsiFile psiFile = directory.findFile(ANNOTATIONS_XML);
          if (psiFile instanceof XmlFile) {
            myExternalAnotations.put(virtualFile, (XmlFile)psiFile);
            return (XmlFile)psiFile;
          }
        }
      }
      if (virtualFile != null) {
        final List<OrderEntry> entries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(virtualFile);
        for (OrderEntry entry : entries) {
          if (!(entry instanceof ModuleOrderEntry)) {
            final String[] externalUrls = entry.getUrls(OrderRootType.ANNOTATIONS);
            for (String url : externalUrls) {
              final VirtualFile ext = LocalFileSystem.getInstance().findFileByPath(
                VfsUtil.urlToPath(url) + "/" + packageName.replace(".", "/") + "/" + ANNOTATIONS_XML);
              if (ext != null) {
                final PsiFile psiFile = myPsiManager.findFile(ext);
                if (psiFile instanceof XmlFile) {
                  myExternalAnotations.put(virtualFile, (XmlFile)psiFile);
                  return (XmlFile)psiFile;
                }
              }
            }
            break;
          }
        }
        myExternalAnotations.put(virtualFile, null);
      }
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
  private static String getNormalizedExternalName(PsiModifierListOwner owner) {
    String externalName = PsiFormatUtil.getExternalName(owner);
    if (externalName != null) {
      if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(owner, PsiMethod.class);
        if (method != null) {
          externalName = externalName.substring(0, externalName.lastIndexOf(' ') + 1) + method.getParameterList().getParameterIndex((PsiParameter)owner);
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

    public MyExternalPromptDialog(final Project project) {
      super(project, ProjectBundle.message("external.annotations.suggestion.message"), ProjectBundle.message("external.annotation.prompt"), Messages.getQuestionIcon());
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
      return new Action[] {okAction, new AbstractAction(externalName) {
        {
          assignMnemonic(externalName, this);
        }
        public void actionPerformed(final ActionEvent e) {
          if (canBeHidden()) {
            setToBeShown(toBeShown(), true);
          }
          close(2);
        }
      } , getCancelAction()};
    }

    protected boolean isToBeShown() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment() || ApplicationManager.getApplication().isUnitTestMode()) return false;
      final String value = PropertiesComponent.getInstance(myProject).getValue(EXTERNAL_ANNOTATIONS_PROPERTY);
      return value == null || Boolean.valueOf(value).booleanValue();
    }

    protected void setToBeShown(boolean value, boolean onOk) {
      PropertiesComponent.getInstance(myProject).setValue(EXTERNAL_ANNOTATIONS_PROPERTY, String.valueOf(value));
    }

    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }
  }
}