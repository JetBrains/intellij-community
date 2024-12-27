// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.EditorTextField;
import com.intellij.util.TextFieldCompletionProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class MethodBrowser extends BrowseModuleValueActionListener<JComponent> {

  public MethodBrowser(final Project project) {
    super(project);
  }

  protected abstract String getClassName();
  protected abstract ConfigurationModuleSelector getModuleSelector();
  protected abstract Condition<PsiMethod> getFilter(PsiClass testClass);

  @Override
  protected String showDialog() {
    final String className = getClassName();
    if (className.trim().isEmpty()) {
      Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"),
                                 ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
      return null;
    }

    final PsiClass testClass = getTestClass(className);
    if (testClass == null) {
      Messages.showMessageDialog(getField(), ExecutionBundle.message("class.does.not.exists.error.message", className),
                                 ExecutionBundle.message("cannot.browse.method.dialog.title"),
                                 Messages.getInformationIcon());
      return null;
    }
    final MethodListDlg dlg = new MethodListDlg(testClass, getFilter(testClass), getField());
    if (dlg.showAndGet()) {
      final PsiMethod method = dlg.getSelected();
      if (method != null) {
        return method.getName();
      }
    }
    return null;
  }

  private PsiClass getTestClass(String className) {
    final ConfigurationModuleSelector selector = getModuleSelector();
    return ActionUtil.underModalProgress(getProject(),
                                  ExecutionBundle.message("browse.method.dialog.looking.for.class"),
                                  () -> selector.findClass(className)
    );
  }

  public void installCompletion(EditorTextField field) {
    new MyTextFieldCompletionProvider().apply(field);
  }

  private class MyTextFieldCompletionProvider extends TextFieldCompletionProvider implements DumbAware {
    @Override
    protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
      final String className = getClassName();
      if (className.trim().isEmpty()) {
        return;
      }
      final PsiClass testClass = getModuleSelector().findClass(className);
      if (testClass == null) return;
      final Condition<PsiMethod> filter = getFilter(testClass);
      for (PsiMethod psiMethod : testClass.getAllMethods()) {
        if (filter.value(psiMethod)) {
          result.addElement(LookupElementBuilder.create(psiMethod.getName()));
        }
      }
    }
  }

}
