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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class StaticImportMethodQuestionAction implements QuestionAction {
  private static final Logger LOG = Logger.getInstance("#" + StaticImportMethodQuestionAction.class.getName());
  private final Project myProject;
  private final Editor myEditor;
  private List<PsiMethod> myCandidates;
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;

  public StaticImportMethodQuestionAction(Project project,
                                          Editor editor,
                                          List<PsiMethod> candidates,
                                          SmartPsiElementPointer<PsiMethodCallExpression> methodCall) {
    myProject = project;
    myEditor = editor;
    myCandidates = candidates;
    myMethodCall = methodCall;
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null || !element.isValid()){
      return false;
    }

    for (PsiMethod targetMethod : myCandidates) {
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

  private void doImport(final PsiMethod toImport) {
    final Project project = toImport.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              PsiMethodCallExpression element = myMethodCall.getElement();
              if (element != null) {
                AddSingleMemberStaticImportAction.bindAllClassRefs(element.getContainingFile(), toImport, toImport.getName(), toImport.getContainingClass());
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });

      }
    }, QuickFixBundle.message("add.import"), this);

  }

  private void chooseAndImport(final Editor editor, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doImport(myCandidates.get(0));
      return;
    }
    final BaseListPopupStep<PsiMethod> step =
      new BaseListPopupStep<PsiMethod>(QuickFixBundle.message("method.to.import.chooser.title"), myCandidates) {

        @Override
        public PopupStep onChosen(PsiMethod selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            LOG.assertTrue(selectedValue.isValid());
            doImport(selectedValue);
            return FINAL_CHOICE;
          }

          String qname = PsiUtil.getMemberQualifiedName(selectedValue);
          if (qname == null) return FINAL_CHOICE;
          List<String> excludableStrings = AddImportAction.getAllExcludableStrings(qname);
          return new BaseListPopupStep<String>(null, excludableStrings) {
            @NotNull
            @Override
            public String getTextFor(String value) {
              return "Exclude '" + value + "' from auto-import";
            }

            @Override
            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
              if (finalChoice) {
                AddImportAction.excludeFromImport(project, selectedValue);
              }

              return super.onChosen(selectedValue, finalChoice);
            }
          };
        }

        @Override
        public boolean hasSubstep(PsiMethod selectedValue) {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(PsiMethod value) {
          return ObjectUtils.assertNotNull(value.getName());
        }

        @Override
        public Icon getIconFor(PsiMethod aValue) {
          return aValue.getIcon(0);
        }
      };

    final ListPopupImpl popup = new ListPopupImpl(step) {
      final PopupListElementRenderer rightArrow = new PopupListElementRenderer(this);
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new MethodCellRenderer(true, PsiFormatUtilBase.SHOW_NAME){

          @Nullable
          @Override
          protected TextAttributes getNavigationItemAttributes(Object value) {
            TextAttributes attrs = super.getNavigationItemAttributes(value);
            if (value instanceof PsiMethod && !((PsiMethod)value).isDeprecated()) {
              PsiClass psiClass = ((PsiMethod)value).getContainingClass();
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
}


