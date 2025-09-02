// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class MoveClassToModuleFix implements IntentionAction {
  private final Map<PsiClass, Module> myModules = new LinkedHashMap<>();
  private final String myReferenceName;
  private final Module myCurrentModule;
  private final PsiDirectory mySourceRoot;
  private static final Logger LOG = Logger.getInstance(MoveClassToModuleFix.class);

  private MoveClassToModuleFix(@NotNull String referenceName, @NotNull Module currentModule, @NotNull PsiDirectory root, @NotNull PsiElement psiElement) {
    myReferenceName = referenceName;
    myCurrentModule = currentModule;
    mySourceRoot = root;
    Project project = psiElement.getProject();
    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (!(psiFile instanceof PsiJavaFile)) continue;
      if (aClass.getQualifiedName() == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        myModules.put(aClass, classModule);
      }
    }
  }

  @Override
  public @NotNull String getText() {
    if (myModules.size() == 1) {
      PsiClass aClass = myModules.keySet().iterator().next();
      return QuickFixBundle
        .message("move.0.from.module.1.to.2", aClass.getQualifiedName(), myModules.get(aClass).getName(), myCurrentModule.getName());
    }
    return QuickFixBundle.message("move.0.in.1", myReferenceName, myCurrentModule.getName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.move.it");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return !myModules.isEmpty() && ContainerUtil.all(myModules.keySet(), PsiElement::isValid);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (myModules.size() == 1) {
      moveClass(project, editor, psiFile, myModules.keySet().iterator().next());
    }
    else {
      LOG.assertTrue(editor != null);
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(new ArrayList<>(myModules.keySet()))
        .setTitle(QuickFixBundle.message("choose.class.to.move.popup.title"))
        .setRenderer(new PsiElementListCellRenderer<PsiClass>() {
          @Override
          public String getElementText(PsiClass psiClass) {
            return psiClass.getQualifiedName();
          }

          @Override
          protected @Nullable String getContainerText(PsiClass element, String name) {
            return null;
          }
        })
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback(value -> moveClass(project, editor, psiFile, value))
        .createPopup()
        .showInBestPositionFor(editor);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    @NlsSafe String name = myReferenceName;
    if (name == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    Icon icon = ModuleType.get(myCurrentModule).getIcon();
    HtmlBuilder builder = new HtmlBuilder()
      .append(name)
      .append(" ").append(HtmlChunk.htmlEntity("&rarr;")).append(" ")
      .append(HtmlChunk.icon("module", icon))
      .nbsp()
      .append(myCurrentModule.getName());
    return new IntentionPreviewInfo.Html(builder.wrapWith("p"));  }

  private void moveClass(Project project, Editor editor, PsiFile psiFile, PsiClass aClass) {
    RefactoringActionHandler moveHandler = RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    DataContext dataContext = EditorUtil.getEditorDataContext(editor);
    String fqName = aClass.getQualifiedName();
    LOG.assertTrue(fqName != null);
    PsiDirectory directory = PackageUtil
      .findOrCreateDirectoryForPackage(myCurrentModule, StringUtil.getPackageName(fqName), mySourceRoot, true);
    DataContext context = directory == null ? dataContext :
                          SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT, directory, dataContext);

    moveHandler.invoke(project, new PsiElement[]{aClass}, context);
    PsiReference reference = psiFile.findReferenceAt(editor.getCaretModel().getOffset());
    PsiClass newClass = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.moduleScope(myCurrentModule));
    if (reference != null && newClass != null) {
      QuestionAction action = new AddImportAction(project, reference, editor, newClass);
      action.execute();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiJavaCodeReferenceElement reference) {
    PsiElement psiElement = reference.getElement();
    @NonNls String referenceName = reference.getRangeInElement().substring(psiElement.getText());
    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return;
    PsiDirectory dir = containingFile.getContainingDirectory();
    if (dir == null) return;

    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;
    List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(currentModule).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    if (sourceRoots.isEmpty()) return;
    PsiDirectory sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceRoots.get(0));
    if (sourceDirectory == null) return;

    VirtualFile vsourceRoot = fileIndex.getSourceRootForFile(classVFile);
    if (vsourceRoot == null) return;
    PsiDirectory sourceRoot = PsiManager.getInstance(project).findDirectory(vsourceRoot);
    if (sourceRoot == null) return;

    registrar.register(new MoveClassToModuleFix(referenceName, currentModule, sourceRoot, psiElement));
  }
}
