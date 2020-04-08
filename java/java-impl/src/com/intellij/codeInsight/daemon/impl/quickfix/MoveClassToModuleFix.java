// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class MoveClassToModuleFix implements IntentionAction {
  private final Map<PsiClass, Module> myModules = new LinkedHashMap<>();
  private final String myReferenceName;
  private final Module myCurrentModule;
  private final PsiDirectory mySourceRoot;
  private static final Logger LOG = Logger.getInstance(MoveClassToModuleFix.class);

  public MoveClassToModuleFix(String referenceName, Module currentModule, PsiDirectory root, PsiElement psiElement) {
    myReferenceName = referenceName;
    myCurrentModule = currentModule;
    mySourceRoot = root;
    final Project project = psiElement.getProject();
    final PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (final PsiClass aClass : classes) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      final PsiFile psiFile = aClass.getContainingFile();
      if (!(psiFile instanceof PsiJavaFile)) continue;
      if (aClass.getQualifiedName() == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      final Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
        myModules.put(aClass, classModule);
      }
    }
  }

  @Override
  @NotNull
  public String getText() {
    if (myModules.size() == 1) {
      final PsiClass aClass = myModules.keySet().iterator().next();
      return QuickFixBundle
        .message("move.0.from.module.1.to.2", aClass.getQualifiedName(), myModules.get(aClass).getName(), myCurrentModule.getName());
    }
    return QuickFixBundle.message("move.0.in.1", myReferenceName, myCurrentModule.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.move.it");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return !myModules.isEmpty() && myModules.keySet().stream().allMatch(PsiElement::isValid);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (myModules.size() == 1) {
      moveClass(project, editor, file, myModules.keySet().iterator().next());
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

          @Nullable
          @Override
          protected String getContainerText(PsiClass element, String name) {
            return null;
          }

          @Override
          protected int getIconFlags() {
            return 0;
          }
        })
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback((value) -> moveClass(project, editor, file, value))
        .createPopup()
        .showInBestPositionFor(editor);
    }
  }

  private void moveClass(Project project, Editor editor, PsiFile file, PsiClass aClass) {
    RefactoringActionHandler moveHandler = RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    DataManager dataManager = DataManager.getInstance();
    DataContext dataContext = dataManager.getDataContext();
    final String fqName = aClass.getQualifiedName();
    LOG.assertTrue(fqName != null);
    PsiDirectory directory = PackageUtil
      .findOrCreateDirectoryForPackage(myCurrentModule, StringUtil.getPackageName(fqName), mySourceRoot, true);
    DataContext context = SimpleDataContext.getSimpleContext(LangDataKeys.TARGET_PSI_ELEMENT.getName(), directory, dataContext);

    moveHandler.invoke(project, new PsiElement[]{aClass}, context);
    PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    PsiClass newClass = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.moduleScope(myCurrentModule));
    if (reference != null && newClass != null) {
      final QuestionAction action = new AddImportAction(project, reference, editor, newClass);
      action.execute();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void registerFixes(QuickFixActionRegistrar registrar, final PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.getElement();
    @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());
    Project project = psiElement.getProject();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return;
    PsiDirectory dir = containingFile.getContainingDirectory();
    if (dir == null) return;

    VirtualFile classVFile = containingFile.getVirtualFile();
    if (classVFile == null) return;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module currentModule = fileIndex.getModuleForFile(classVFile);
    if (currentModule == null) return;
    List<VirtualFile> sourceRoots = ModuleRootManager.getInstance(currentModule).getSourceRoots(JavaModuleSourceRootTypes.SOURCES);
    if (sourceRoots.isEmpty()) return;
    final PsiDirectory sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceRoots.get(0));
    if (sourceDirectory == null) return;

    VirtualFile vsourceRoot = fileIndex.getSourceRootForFile(classVFile);
    if (vsourceRoot == null) return;
    final PsiDirectory sourceRoot = PsiManager.getInstance(project).findDirectory(vsourceRoot);
    if (sourceRoot == null) return;

    registrar.register(new MoveClassToModuleFix(referenceName, currentModule, sourceRoot, psiElement));
  }
}
