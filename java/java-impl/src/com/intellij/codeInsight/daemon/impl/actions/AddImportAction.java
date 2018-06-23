
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.application.options.editor.AutoImportOptionsConfigurable;
import com.intellij.application.options.editor.JavaAutoImportOptions;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AddImportAction implements QuestionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.actions.AddImportAction");

  private final Project myProject;
  private final PsiReference myReference;
  private final PsiClass[] myTargetClasses;
  private final Editor myEditor;

  public AddImportAction(@NotNull Project project,
                         @NotNull PsiReference ref,
                         @NotNull Editor editor,
                         @NotNull PsiClass... targetClasses) {
    myProject = project;
    myReference = ref;
    myTargetClasses = targetClasses;
    myEditor = editor;
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!myReference.getElement().isValid()){
      return false;
    }

    for (PsiClass myTargetClass : myTargetClasses) {
      if (!myTargetClass.isValid()) {
        return false;
      }
    }

    if (myTargetClasses.length == 1){
      addImport(myReference, myTargetClasses[0]);
    }
    else{
      chooseClassAndImport();
    }
    return true;
  }

  private void chooseClassAndImport() {
    CodeInsightUtil.sortIdenticalShortNamedMembers(myTargetClasses, myReference);

    final BaseListPopupStep<PsiClass> step =
      new BaseListPopupStep<PsiClass>(QuickFixBundle.message("class.to.import.chooser.title"), myTargetClasses) {
        @Override
        public boolean isAutoSelectionEnabled() {
          return false;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(PsiClass selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            return doFinalStep(() -> {
              PsiDocumentManager.getInstance(myProject).commitAllDocuments();
              addImport(myReference, selectedValue);
            });
          }

          return getExcludesStep(selectedValue.getQualifiedName(), myProject);
        }

        @Override
        public boolean hasSubstep(PsiClass selectedValue) {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(PsiClass value) {
          return ObjectUtils.assertNotNull(value.getQualifiedName());
        }

        @Override
        public Icon getIconFor(PsiClass aValue) {
          return aValue.getIcon(0);
        }
      };
    ListPopupImpl popup = new ListPopupImpl(step) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        final PopupListElementRenderer baseRenderer = (PopupListElementRenderer)super.getListElementRenderer();
        final DefaultPsiElementCellRenderer psiRenderer = new DefaultPsiElementCellRenderer();
        return new ListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            panel.add(baseRenderer.getNextStepLabel(), BorderLayout.EAST);
            panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus));
            return panel;
          }
        };
      }
    };
    NavigationUtil.hidePopupIfDumbModeStarts(popup, myProject);
    popup.showInBestPositionFor(myEditor);
  }

  @Nullable
  public static PopupStep getExcludesStep(String qname, final Project project) {
    if (qname == null) return PopupStep.FINAL_CHOICE;

    List<String> toExclude = getAllExcludableStrings(qname);

    return new BaseListPopupStep<String>(null, toExclude) {
      @NotNull
      @Override
      public String getTextFor(String value) {
        return "Exclude '" + value + "' from auto-import";
      }

      @Override
      public PopupStep onChosen(String selectedValue, boolean finalChoice) {
        if (finalChoice) {
          excludeFromImport(project, selectedValue);
        }

        return super.onChosen(selectedValue, finalChoice);
      }
    };
  }
  
  public static void excludeFromImport(final Project project, final String prefix) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) return;

      final AutoImportOptionsConfigurable configurable = new AutoImportOptionsConfigurable(project);
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
        final JavaAutoImportOptions options = ContainerUtil.findInstance(configurable.getConfigurables(), JavaAutoImportOptions.class);
        options.addExcludePackage(prefix);
      });
    });
  }

  public static List<String> getAllExcludableStrings(@NotNull String qname) {
    List<String> toExclude = new ArrayList<>();
    while (true) {
      toExclude.add(qname);
      final int i = qname.lastIndexOf('.');
      if (i < 0 || i == qname.indexOf('.')) break;
      qname = qname.substring(0, i);
    }
    return toExclude;
  }

  private void addImport(final PsiReference ref, final PsiClass targetClass) {
    DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
      if (!ref.getElement().isValid() || !targetClass.isValid() || ref.resolve() == targetClass) {
        return;
      }

      StatisticsManager.getInstance().incUseCount(JavaStatisticsManager.createInfo(null, targetClass));
      WriteCommandAction.runWriteCommandAction(myProject, QuickFixBundle.message("add.import"), null,
                                               () -> _addImport(ref, targetClass),
                                               ref.getElement().getContainingFile());
    });
  }

  private void _addImport(PsiReference ref, PsiClass targetClass) {
    try{
      bindReference(ref, targetClass);
      if (CodeInsightWorkspaceSettings.getInstance(myProject).optimizeImportsOnTheFly) {
        Document document = myEditor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        new OptimizeImportsProcessor(myProject, psiFile).runWithoutProgress();
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected void bindReference(PsiReference ref, PsiClass targetClass) {
    ref.bindToElement(targetClass);
  }
}
