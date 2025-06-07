// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


public class CopyAbstractMethodImplementationHandler {
  private static final Logger LOG = Logger.getInstance(CopyAbstractMethodImplementationHandler.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;
  private PsiClass mySourceClass;
  private final List<PsiClass> myTargetClasses = new ArrayList<>();
  private final List<PsiEnumConstant> myTargetEnumConstants = new ArrayList<>();
  private final List<PsiMethod> mySourceMethods = new ArrayList<>();

  public CopyAbstractMethodImplementationHandler(final Project project, final Editor editor, final PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> searchExistingImplementations()), CodeInsightBundle.message("searching.for.implementations"), false, myProject);
    if (mySourceMethods.isEmpty()) {
      Messages.showErrorDialog(myProject, JavaBundle.message("copy.abstract.method.no.existing.implementations.found"),
                               JavaBundle.message("copy.abstract.method.title"));
      return;
    }
    if (mySourceMethods.size() == 1) {
      copyImplementation(mySourceMethods.get(0));
    }
    else {
      mySourceMethods.sort((o1, o2) -> {
        PsiClass c1 = o1.getContainingClass();
        PsiClass c2 = o2.getContainingClass();
        return Comparing.compare(c1.getName(), c2.getName());
      });
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(mySourceMethods)
        .setRenderer(new MethodCellRenderer(true))
        .setItemChosenCallback((element) -> copyImplementation(element))
        .setTitle(JavaBundle.message("copy.abstract.method.popup.title"))
        .createPopup()
        .showInBestPositionFor(myEditor);
    }
  }

  private void searchExistingImplementations() {
    mySourceClass = myMethod.getContainingClass();
    if (!mySourceClass.isValid()) return;
    for (PsiClass inheritor : ClassInheritorsSearch.search(mySourceClass).asIterable()) {
      if (!inheritor.isInterface()) {
        PsiMethod method = ImplementAbstractMethodAction.findExistingImplementation(inheritor, myMethod);
        if (method != null && !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          mySourceMethods.add(method);
        }
        else if (method == null) {
          myTargetClasses.add(inheritor);
        }
      }
    }
    for (Iterator<PsiClass> targetClassIterator = myTargetClasses.iterator(); targetClassIterator.hasNext();) {
      PsiClass targetClass = targetClassIterator.next();
      if (containsAnySuperClass(targetClass)) {
        targetClassIterator.remove();
      }
    }
    if (mySourceClass.isEnum()) {
      for (PsiField field : mySourceClass.getFields()) {
        if (field instanceof PsiEnumConstant enumConstant){
          final PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
          if (initializingClass == null) {
            myTargetEnumConstants.add(enumConstant);
          }
        }
      }
    }
  }

  private boolean containsAnySuperClass(final PsiClass targetClass) {
    PsiClass superClass = targetClass.getSuperClass();
    while(superClass != null) {
      if (myTargetClasses.contains(superClass)) return true;
      superClass = superClass.getSuperClass();
    }
    return false;
  }

  private void copyImplementation(final PsiMethod sourceMethod) {
    final List<PsiMethod> generatedMethods = new ArrayList<>();
    WriteCommandAction.writeCommandAction(myProject, myTargetClasses.isEmpty() ? myTargetEnumConstants : myTargetClasses).run(() -> {
      for (PsiEnumConstant enumConstant : myTargetEnumConstants) {
        PsiClass initializingClass = enumConstant.getOrCreateInitializingClass();
        myTargetClasses.add(initializingClass);
      }
      for (PsiClass psiClass : myTargetClasses) {
        final Collection<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethod(psiClass, myMethod, true);
        final Iterator<PsiMethod> iterator = methods.iterator();
        if (!iterator.hasNext()) continue;
        PsiMethod overriddenMethod = iterator.next();
        final PsiCodeBlock body = overriddenMethod.getBody();
        final PsiCodeBlock sourceBody = sourceMethod.getBody();
        assert body != null && sourceBody != null;
        ChangeContextUtil.encodeContextInfo(sourceBody, true);
        final PsiElement newBody = body.replace(sourceBody.copy());
        ChangeContextUtil.decodeContextInfo(newBody, psiClass, null);
        final PsiDocComment docComment = overriddenMethod.getDocComment();
        if (docComment != null) {
          try {
            docComment.delete();
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(mySourceClass, psiClass, PsiSubstitutor.EMPTY);
        PsiElement anchor = OverrideImplementUtil.getDefaultAnchorToOverrideOrImplement(psiClass, sourceMethod, substitutor);
        try {
          if (anchor != null) {
            overriddenMethod = (PsiMethod)anchor.getParent().addBefore(overriddenMethod, anchor);
          }
          else {
            overriddenMethod = (PsiMethod)psiClass.addBefore(overriddenMethod, psiClass.getRBrace());
          }
          generatedMethods.add(overriddenMethod);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    if (!generatedMethods.isEmpty()) {
      PsiMethod target = generatedMethods.get(0);
      PsiFile psiFile = target.getContainingFile();
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(psiFile.getProject());
      Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(psiFile.getProject(), psiFile.getVirtualFile()), false);
      if (editor != null) {
        GenerateMembersUtil.positionCaret(editor, target, true);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
    }
  }
}
