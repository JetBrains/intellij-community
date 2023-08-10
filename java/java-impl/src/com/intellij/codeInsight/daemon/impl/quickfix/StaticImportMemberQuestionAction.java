// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.ide.util.PsiClassRenderingInfo;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class StaticImportMemberQuestionAction<T extends PsiMember> implements QuestionAction {
  private static final Logger LOG = Logger.getInstance(StaticImportMemberQuestionAction.class);
  private final Project myProject;
  private final Editor myEditor;
  private final List<? extends T> myCandidates;
  private final SmartPsiElementPointer<? extends PsiElement> myRef;

  StaticImportMemberQuestionAction(@NotNull Project project,
                                   Editor editor,
                                   @NotNull List<? extends T> candidates,
                                   @NotNull SmartPsiElementPointer<? extends PsiElement> ref) {
    myProject = project;
    myEditor = editor;
    myCandidates = candidates;
    myRef = ref;
  }

  @NotNull
  protected @NlsContexts.PopupTitle String getPopupTitle() {
    return QuickFixBundle.message("method.to.import.chooser.title");
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = myRef.getElement();
    if (element == null || !element.isValid()){
      return false;
    }

    for (T targetMethod : myCandidates) {
      if (!targetMethod.isValid()) {
        return false;
      }
    }

    if (myCandidates.size() == 1){
      doImport(myCandidates.get(0));
    }
    else{
      chooseAndImport(myEditor, myProject);
    }
    return true;
  }

  protected void doImport(@NotNull T toImport) {
    Project project = toImport.getProject();
    PsiElement element = myRef.getElement();
    if (element == null) return;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("add.import"), null, () ->
      AddSingleMemberStaticImportAction.bindAllClassRefs(element.getContainingFile(), toImport, toImport.getName(), toImport.getContainingClass()));
  }

  private void chooseAndImport(@NotNull Editor editor, @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doImport(myCandidates.get(0));
      return;
    }
    BaseListPopupStep<T> step =
      new BaseListPopupStep<>(getPopupTitle(), myCandidates) {

        @Override
        public boolean isAutoSelectionEnabled() {
          return false;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            return doFinalStep(() -> {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              if (selectedValue.isValid()) {
                doImport(selectedValue);
              }
            });
          }

          return AddImportAction.getExcludesStep(project, PsiUtil.getMemberQualifiedName(selectedValue));
        }

        @Override
        public boolean hasSubstep(T selectedValue) {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(T value) {
          return getElementPresentableName(value);
        }

        @Override
        public Icon getIconFor(T aValue) {
          return aValue.getIcon(0);
        }
      };

    JBPopup popup = JBPopupFactory.getInstance().createListPopup(project, step, superRenderer -> {
      GroupedItemsListRenderer<T> rightArrow = (GroupedItemsListRenderer<T>)superRenderer;
      StaticMemberRenderer psiRenderer = new StaticMemberRenderer();
      return (ListCellRenderer<T>)(list, value, index, isSelected, cellHasFocus) -> {
        JPanel panel = new JPanel(new BorderLayout());
        Component psiRendererComponent = psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        rightArrow.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        JLabel arrowLabel = rightArrow.getNextStepLabel();
        arrowLabel.setBackground(psiRendererComponent.getBackground());
        panel.setBackground(psiRendererComponent.getBackground());
        panel.add(psiRendererComponent, BorderLayout.CENTER);
        panel.add(arrowLabel, BorderLayout.EAST);
        return panel;
      };
    });
    popup.showInBestPositionFor(editor);
  }

  private static final class StaticMemberRenderer extends PsiElementListCellRenderer<PsiMember> {
    @Override
    public @NotNull String getElementText(PsiMember element) {
      return getElementPresentableName(element);
    }

    @Override
    public String getContainerText(PsiMember element, String name) {
      return PsiClassRenderingInfo.getContainerTextStatic(element);
    }

    @Nullable
    @Override
    protected TextAttributes getNavigationItemAttributes(Object value) {
      TextAttributes attrs = super.getNavigationItemAttributes(value);
      if (value instanceof PsiDocCommentOwner && !((PsiDocCommentOwner)value).isDeprecated()) {
        PsiClass psiClass = ((PsiMember)value).getContainingClass();
        if (psiClass != null && psiClass.isDeprecated()) {
          return TextAttributes.merge(attrs, super.getNavigationItemAttributes(psiClass));
        }
      }
      return attrs;
    }
  }

  private static @NlsSafe @NotNull String getElementPresentableName(@NotNull PsiMember element) {
    PsiClass aClass = element.getContainingClass();
    LOG.assertTrue(aClass != null);
    return ClassPresentationUtil.getNameForClass(aClass, false) + "." + element.getName();
  }
}
