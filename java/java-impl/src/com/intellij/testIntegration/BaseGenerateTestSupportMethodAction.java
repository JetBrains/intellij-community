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
package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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

  private static PsiClass findTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return TestIntegrationUtils.findOuterClass(element);
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    List<TestFramework> frameworks = findSuitableFrameworks(targetClass);
    if (frameworks.isEmpty()) return false;

    for (TestFramework each : frameworks) {
      if (isValidFor(targetClass, each)) return true;
    }
    return false;
  }

  protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
    return true;
  }

  private static List<TestFramework> findSuitableFrameworks(PsiClass targetClass) {
    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework each : frameworks) {
      if (each.isTestClass(targetClass)) {
        return Collections.singletonList(each);
      }
    }

    List<TestFramework> result = new SmartList<TestFramework>();
    for (TestFramework each : frameworks) {
      if (each.isPotentialTestClass(targetClass)) {
        result.add(each);
      }
    }
    return result;
  }

  private static class MyHandler implements CodeInsightActionHandler {
    private TestIntegrationUtils.MethodKind myMethodKind;

    private MyHandler(TestIntegrationUtils.MethodKind methodKind) {
      myMethodKind = methodKind;
    }

    public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
      final PsiClass targetClass = findTargetClass(editor, file);
      final List<TestFramework> frameworks = findSuitableFrameworks(targetClass);
      if (frameworks.isEmpty()) return;

      if (frameworks.size() == 1) {
        doGenerate(editor, file, targetClass, frameworks.get(0));
      } else {
        final JList list = new JList(frameworks.toArray(new TestFramework[frameworks.size()]));
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
    }

    private void doGenerate(final Editor editor, final PsiFile file, final PsiClass targetClass, final TestFramework framework) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            PsiMethod method = generateDummyMethod(editor, file);
            if (method == null) return;

            TestIntegrationUtils.runTestMethodTemplate(myMethodKind,
                                                       framework,
                                                       editor,
                                                       targetClass,
                                                       method,
                                                       "name",
                                                       false);
          }
          catch (IncorrectOperationException e) {
            HintManager.getInstance().showErrorHint(editor, "Cannot generate method: " + e.getMessage());
            LOG.warn(e);
          }
        }
      });
    }

    @Nullable
    private PsiMethod generateDummyMethod(Editor editor, PsiFile file) throws IncorrectOperationException {
      List<GenerationInfo> members = new ArrayList<GenerationInfo>();

      final PsiMethod method = TestIntegrationUtils.createDummyMethod(file.getProject());
      final PsiMethod[] result = new PsiMethod[1];

      members.add(new GenerationInfo() {
        @NotNull
        public PsiMember getPsiMember() {
          return method;
        }

        public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
          result[0] = (PsiMethod)GenerateMembersUtil.insert(aClass, method, anchor, before);
        }
      });

      int offset = findOffsetToInsertMethodTo(editor, file);

      GenerateMembersUtil.insertMembersAtOffset(file, offset, members);
      return CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(result[0]);
    }

    private int findOffsetToInsertMethodTo(Editor editor, PsiFile file) {
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
