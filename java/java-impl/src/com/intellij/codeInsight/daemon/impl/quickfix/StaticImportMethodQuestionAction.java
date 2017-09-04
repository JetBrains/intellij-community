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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StaticImportMethodQuestionAction<T extends PsiMember> implements QuestionAction {
  private static final Logger LOG = Logger.getInstance(StaticImportMethodQuestionAction.class);
  private final Project myProject;
  private final Editor myEditor;
  private List<T> myCandidates;
  private final SmartPsiElementPointer<? extends PsiElement> myRef;

  public StaticImportMethodQuestionAction(Project project,
                                          Editor editor,
                                          List<T> candidates,
                                          SmartPsiElementPointer<? extends PsiElement> ref) {
    myProject = project;
    myEditor = editor;
    myCandidates = candidates;
    myRef = ref;
  }

  @NotNull
  protected String getPopupTitle() {
    return QuickFixBundle.message("method.to.import.chooser.title");
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiElement element = myRef.getElement();
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

  protected void doImport(final T toImport) {
    final Project project = toImport.getProject();
    final PsiElement element = myRef.getElement();
    if (element == null) return;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("add.import"), null, () ->
      AddSingleMemberStaticImportAction.bindAllClassRefs(element.getContainingFile(), toImport, toImport.getName(), toImport.getContainingClass()));
  }

  private void chooseAndImport(final Editor editor, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doImport(myCandidates.get(0));
      return;
    }
    final BaseListPopupStep<T> step =
      new BaseListPopupStep<T>(getPopupTitle(), myCandidates) {
        
        @Override
        public boolean isAutoSelectionEnabled() {
          return false;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }
        
        @Override
        public PopupStep onChosen(T selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            return doFinalStep(() -> {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              LOG.assertTrue(selectedValue.isValid());
              doImport(selectedValue);
            });
          }

          return AddImportAction.getExcludesStep(PsiUtil.getMemberQualifiedName(selectedValue), project);
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

    final ListPopupImpl popup = new ListPopupImpl(step) {
      final PopupListElementRenderer rightArrow = new PopupListElementRenderer(this);
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new PsiElementListCellRenderer<T>() {
          public String getElementText(T element) {
            return getElementPresentableName(element);
          }

          public String getContainerText(final T element, final String name) {
            return PsiClassListCellRenderer.getContainerTextStatic(element);
          }

          public int getIconFlags() {
            return 0;
          }

          @Nullable
          @Override
          protected TextAttributes getNavigationItemAttributes(Object value) {
            TextAttributes attrs = super.getNavigationItemAttributes(value);
            if (value instanceof PsiDocCommentOwner && !((PsiDocCommentOwner)value).isDeprecated()) {
              PsiClass psiClass = ((T)value).getContainingClass();
              if (psiClass != null && psiClass.isDeprecated()) {
                return TextAttributes.merge(attrs, super.getNavigationItemAttributes(psiClass));
              }
            }
            return attrs;
          }

          @Override
          protected DefaultListCellRenderer getRightCellRenderer(final Object value) {
            final DefaultListCellRenderer moduleRenderer = super.getRightCellRenderer(value);
            return new DefaultListCellRenderer(){
              @Override
              public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JPanel panel = new JPanel(new BorderLayout());
                if (moduleRenderer != null) {
                  Component moduleComponent = moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                  if (!isSelected) {
                    moduleComponent.setBackground(getBackgroundColor(value));
                  }
                  panel.add(moduleComponent, BorderLayout.CENTER);
                }
                rightArrow.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                Component rightArrowComponent = rightArrow.getNextStepLabel();
                panel.add(rightArrowComponent, BorderLayout.EAST);
                return panel;
              }
            };
          }
        };
      }
    };
    popup.showInBestPositionFor(editor);
  }

  private String getElementPresentableName(T element) {
    final PsiClass aClass = element.getContainingClass();
    LOG.assertTrue(aClass != null);
    return ClassPresentationUtil.getNameForClass(aClass, false) + "." + element.getName();
  }
}


