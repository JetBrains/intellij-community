/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class BaseGenerateTestSupportMethodAction extends BaseGenerateAction {
  protected static final Logger LOG = Logger.getInstance("#" + BaseGenerateTestSupportMethodAction.class.getName());

  public BaseGenerateTestSupportMethodAction(TestIntegrationUtils.MethodKind methodKind) {
    super(new MyHandler(methodKind));
  }

  @Override
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    return findTargetClass(editor, file);
  }

  @Nullable
  private static PsiClass findTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element == null ? null : TestIntegrationUtils.findOuterClass(element);
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(targetClass);
    if (frameworks.isEmpty()) return false;

    for (TestFramework each : frameworks) {
      if (isValidFor(targetClass, each)) return true;
    }
    return false;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file instanceof PsiCompiledElement) return false;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClass targetClass = getTargetClass(editor, file);
    return targetClass != null && isValidForClass(targetClass);
  }


  protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
    return true;
  }


  private static class MyHandler implements CodeInsightActionHandler {
    private TestIntegrationUtils.MethodKind myMethodKind;

    private MyHandler(TestIntegrationUtils.MethodKind methodKind) {
      myMethodKind = methodKind;
    }

    public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
      final PsiClass targetClass = findTargetClass(editor, file);
      final List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(targetClass);
      if (frameworks.isEmpty()) return;

      if (frameworks.size() == 1) {
        doGenerate(editor, file, targetClass, frameworks.get(0));
        return;
      }

      final JList list = new JBList(frameworks.toArray(new TestFramework[frameworks.size()]));
      list.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          Component result = super.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
          if (value == null) return result;
          TestFramework framework = (TestFramework)value;

          setIcon(framework.getIcon());
          setText(framework.getName());

          return result;
        }
      });

      final Runnable runnable = new Runnable() {
        public void run() {
          TestFramework selected = (TestFramework)list.getSelectedValue();
          if (selected == null) return;
          doGenerate(editor, file, targetClass, selected);
        }
      };

      PopupChooserBuilder builder = new PopupChooserBuilder(list);
      builder.setFilteringEnabled(new Function<Object, String>() {
        @Override
        public String fun(Object o) {
          return ((TestFramework)o).getName();
        }
      });

      builder
        .setTitle("Choose Framework")
        .setItemChoosenCallback(runnable)
        .setMovable(true)
        .createPopup().showInBestPositionFor(editor);
    }

    private void doGenerate(final Editor editor, final PsiFile file, final PsiClass targetClass, final TestFramework framework) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) return;

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
            PsiMethod method = generateDummyMethod(editor, file);
            if (method == null) return;

            TestIntegrationUtils.runTestMethodTemplate(myMethodKind, framework, editor, targetClass, method, "name", false);
          }
          catch (IncorrectOperationException e) {
            HintManager.getInstance().showErrorHint(editor, "Cannot generate method: " + e.getMessage());
            LOG.warn(e);
          }
        }
      });
    }

    @Nullable
    private static PsiMethod generateDummyMethod(Editor editor, PsiFile file) throws IncorrectOperationException {
      final PsiMethod method = TestIntegrationUtils.createDummyMethod(file);
      final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);

      int offset = findOffsetToInsertMethodTo(editor, file);
      GenerateMembersUtil.insertMembersAtOffset(file, offset, Collections.singletonList(info));

      final PsiMethod member = info.getPsiMember();
      return member != null ? CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(member) : null;
    }

    private static int findOffsetToInsertMethodTo(Editor editor, PsiFile file) {
      int result = editor.getCaretModel().getOffset();

      PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);

      while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
        result = classAtCursor.getTextRange().getEndOffset();
        classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
      }

      return result;
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
