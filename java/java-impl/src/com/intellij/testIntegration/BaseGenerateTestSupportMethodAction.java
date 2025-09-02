// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class BaseGenerateTestSupportMethodAction extends BaseGenerateAction {
  protected static final Logger LOG = Logger.getInstance(BaseGenerateTestSupportMethodAction.class);

  public BaseGenerateTestSupportMethodAction(TestIntegrationUtils.MethodKind methodKind) {
    super(new MyHandler(methodKind));
  }

  @Override
  public @Nullable AnAction createEditTemplateAction(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final PsiClass targetClass = editor == null || file == null ? null : getTargetClass(editor, file);
    if (targetClass != null) {
      final List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(targetClass);
      final TestIntegrationUtils.MethodKind methodKind = ((MyHandler)getHandler(dataContext)).myMethodKind;
      if (!frameworks.isEmpty()) {
        return new AnAction(JavaBundle.message("action.text.edit.template")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            chooseAndPerform(editor, frameworks, framework -> {
              final FileTemplateDescriptor descriptor = methodKind.getFileTemplateDescriptor(framework);
              if (descriptor != null) {
                final String fileName = descriptor.getFileName();
                AllFileTemplatesConfigurable.editCodeTemplate(FileUtilRt.getNameWithoutExtension(fileName), project);
              } else {
                String message = JavaBundle.message(
                  "generate.test.support.method.error.no.template.found.for.framework", framework.getName(),
                  BaseGenerateTestSupportMethodAction.this.getTemplatePresentation().getText());
                HintManager.getInstance().showErrorHint(editor, message);
              }
            });
          }
        };
      }
    }
    return null;
  }

  @Override
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    return findTargetClass(editor, file);
  }

  private static @Nullable PsiClass findTargetClass(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (containingClass == null) {
      return null;
    }
    final List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(containingClass);
    for (TestFramework framework : frameworks) {
      if (framework instanceof JavaTestFramework && ((JavaTestFramework)framework).acceptNestedClasses()) {
        return containingClass;
      }
    }
    return TestIntegrationUtils.findOuterClass(element);
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
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (psiFile instanceof PsiCompiledElement) return false;

    PsiClass targetClass = getTargetClass(editor, psiFile);
    return targetClass != null && isValidForClass(targetClass);
  }


  protected boolean isValidFor(PsiClass targetClass, TestFramework framework) {
    return ((MyHandler)getHandler()).myMethodKind.getFileTemplateDescriptor(framework) != null;
  }

  private static void chooseAndPerform(Editor editor, List<? extends TestFramework> frameworks, final Consumer<? super TestFramework> consumer) {
    if (frameworks.size() == 1) {
      consumer.consume(frameworks.get(0));
      return;
    }

    DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component result = super.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
        if (value == null) return result;
        TestFramework framework = (TestFramework)value;

        setIcon(framework.getIcon());
        setText(framework.getName());

        return result;
      }
    };
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(frameworks)
      .setRenderer(cellRenderer)
      .setNamerForFiltering(o -> o.getName())
      .setTitle(JavaBundle.message("popup.title.choose.framework"))
      .setItemChosenCallback(consumer)
      .setMovable(true)
      .createPopup().showInBestPositionFor(editor);
  }

  public static class MyHandler implements CodeInsightActionHandler {
    private final TestIntegrationUtils.MethodKind myMethodKind;

    public MyHandler(TestIntegrationUtils.MethodKind methodKind) {
      myMethodKind = methodKind;
    }

    @Override
    public void invoke(@NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
      final PsiClass targetClass = findTargetClass(editor, psiFile);
      final List<TestFramework> frameworks = new ArrayList<>(TestIntegrationUtils.findSuitableFrameworks(targetClass));
      for (Iterator<TestFramework> iterator = frameworks.iterator(); iterator.hasNext(); ) {
        if (myMethodKind.getFileTemplateDescriptor(iterator.next()) == null) {
          iterator.remove();
        }
      }
      if (frameworks.isEmpty()) return;
      final Consumer<TestFramework> consumer = framework -> {
        if (framework == null) return;
        doGenerate(editor, psiFile, targetClass, framework);
      };

      chooseAndPerform(editor, frameworks, consumer);
    }


    private void doGenerate(final Editor editor, final PsiFile file, final PsiClass targetClass, final TestFramework framework) {
      if (framework instanceof JavaTestFramework && ((JavaTestFramework)framework).isSingleConfig()) {
        PsiElement alreadyExist = switch (myMethodKind) {
          case SET_UP -> framework.findSetUpMethod(targetClass);
          case TEAR_DOWN -> framework.findTearDownMethod(targetClass);
          default -> null;
        };

        if (alreadyExist instanceof PsiMethod) {
          editor.getCaretModel().moveToOffset(alreadyExist.getNavigationElement().getTextOffset());
          String message = JavaBundle.message("generate.test.support.method.error.method.already.exists", ((PsiMethod)alreadyExist).getName());
          HintManager.getInstance().showErrorHint(editor, message);
          return;
        }
      }

      if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) return;

      WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
        try {
          PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
          PsiMethod method = generateDummyMethod(file, editor, targetClass);
          if (method == null) return;

          TestIntegrationUtils.runTestMethodTemplate(myMethodKind, framework, editor, targetClass, method, "name", false, null);
        }
        catch (IncorrectOperationException e) {
          String message = JavaBundle.message("generate.test.support.method.error.cannot.generate.method", e.getMessage());
          HintManager.getInstance().showErrorHint(editor, message);
          LOG.warn(e);
        }
      });
    }

    private static @Nullable PsiMethod generateDummyMethod(PsiFile file, Editor editor, PsiClass targetClass) throws IncorrectOperationException {
      final PsiMethod method = TestIntegrationUtils.createDummyMethod(file);
      final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);

      int offset = findOffsetToInsertMethodTo(editor, file, targetClass);
      GenerateMembersUtil.insertMembersAtOffset(file, offset, Collections.singletonList(info));

      final PsiMethod member = info.getPsiMember();
      return member != null ? CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(member) : null;
    }

    private static int findOffsetToInsertMethodTo(Editor editor, PsiFile file, PsiClass targetClass) {
      int result = editor.getCaretModel().getOffset();

      PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);
      if (classAtCursor == targetClass) {
        return result;
      }

      while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
        result = classAtCursor.getTextRange().getEndOffset();
        classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
      }

      return result;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
