package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class CopyAbstractMethodImplementationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.CopyAbstractMethodImplementationHandler");

  private Project myProject;
  private Editor myEditor;
  private PsiMethod myMethod;
  private PsiClass mySourceClass;
  private List<PsiClass> myTargetClasses = new ArrayList<PsiClass>();
  private List<PsiMethod> mySourceMethods = new ArrayList<PsiMethod>();

  public CopyAbstractMethodImplementationHandler(final Project project, final Editor editor, final PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
  }

  public void invoke() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        searchExistingImplementations();
      }
    }, CodeInsightBundle.message("searching.for.implementations"), false, myProject);
    if (mySourceMethods.isEmpty()) {
      Messages.showErrorDialog(myProject, CodeInsightBundle.message("copy.abstract.method.no.existing.implementations.found"),
                               CodeInsightBundle.message("copy.abstract.method.title"));
      return;
    }
    if (mySourceMethods.size() == 1) {
      copyImplementation(mySourceMethods.get(0));
    }
    else {
      Collections.sort(mySourceMethods, new Comparator<PsiMethod>() {
        public int compare(final PsiMethod o1, final PsiMethod o2) {
          PsiClass c1 = o1.getContainingClass();
          PsiClass c2 = o2.getContainingClass();
          return Comparing.compare(c1.getName(), c2.getName());
        }
      });
      final PsiMethod[] methodArray = mySourceMethods.toArray(new PsiMethod[mySourceMethods.size()]);
      final JList list = new JList(methodArray);
      list.setCellRenderer(new MethodCellRenderer(true));
      final Runnable runnable = new Runnable() {
        public void run() {
          int index = list.getSelectedIndex();
          if (index < 0) return;
          PsiMethod element = (PsiMethod)list.getSelectedValue();
          copyImplementation(element);
        }
      };
      new PopupChooserBuilder(list)
        .setTitle(CodeInsightBundle.message("copy.abstract.method.popup.title"))
        .setItemChoosenCallback(runnable)
        .createPopup()
        .showInBestPositionFor(myEditor);
    }
  }

  private void searchExistingImplementations() {
    mySourceClass = myMethod.getContainingClass();
    if (!mySourceClass.isValid()) return;
    for (PsiClass inheritor : ClassInheritorsSearch.search(mySourceClass, mySourceClass.getUseScope(), true)) {
      if (!inheritor.isInterface()) {
        PsiMethod method = ImplementAbstractMethodAction.findExistingImplementation(inheritor, myMethod);
        if (method != null && !method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT)) {
          mySourceMethods.add(method);
        }
        else {
          myTargetClasses.add(inheritor);
        }
      }
    }
  }

  private void copyImplementation(final PsiMethod sourceMethod) {
    final List<PsiMethod> generatedMethods = new ArrayList<PsiMethod>();
    new WriteCommandAction(myProject, getTargetFiles()) {
      protected void run(final Result result) throws Throwable {
        for(PsiClass psiClass: myTargetClasses) {
          final Collection<PsiMethod> methods = OverrideImplementUtil.overrideOrImplementMethod(psiClass, myMethod, true);
          PsiMethod overriddenMethod = methods.iterator().next();
          final PsiCodeBlock body = overriddenMethod.getBody();
          final PsiCodeBlock sourceBody = sourceMethod.getBody();
          assert body != null && sourceBody != null;
          body.replace(sourceBody.copy());

          PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(mySourceClass, psiClass, PsiSubstitutor.EMPTY);
          PsiElement anchor = OverrideImplementUtil.getDefaultAnchorToOverrideOrImplement(psiClass, sourceMethod, substitutor);
          try {
            if (anchor != null) {
              overriddenMethod = (PsiMethod) anchor.getParent().addBefore(overriddenMethod, anchor);
            }
            else {
              overriddenMethod = (PsiMethod) psiClass.addBefore(overriddenMethod, psiClass.getRBrace());
            }
            generatedMethods.add(overriddenMethod);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }.execute();
    if (generatedMethods.size() > 0) {
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

  private PsiFile[] getTargetFiles() {
    Collection<PsiFile> fileList = new HashSet<PsiFile>();
    for(PsiClass psiClass: myTargetClasses) {
      fileList.add(psiClass.getContainingFile());
    }
    return fileList.toArray(new PsiFile[fileList.size()]);
  }

}
