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
package com.intellij.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.EditorTextField;
import com.intellij.util.TextFieldCompletionProvider;
import org.jetbrains.annotations.NotNull;

public abstract class MethodBrowser extends BrowseModuleValueActionListener {

  public MethodBrowser(final Project project) {
    super(project);
  }

  protected abstract String getClassName();
  protected abstract ConfigurationModuleSelector getModuleSelector();
  protected abstract Condition<PsiMethod> getFilter(PsiClass testClass);

  protected String showDialog() {
    final String className = getClassName();
    if (className.trim().length() == 0) {
      Messages.showMessageDialog(getField(), ExecutionBundle.message("set.class.name.message"),
                                 ExecutionBundle.message("cannot.browse.method.dialog.title"), Messages.getInformationIcon());
      return null;
    }
    final PsiClass testClass = getModuleSelector().findClass(className);
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

  public void installCompletion(EditorTextField field) {
    new TextFieldCompletionProvider() {
      @Override
      protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
        final String className = getClassName();
        if (className.trim().length() == 0) {
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
    }.apply(field);
  }
  
}
