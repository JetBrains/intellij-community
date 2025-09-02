// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AddImportAction implements QuestionAction {
  private static final Logger LOG = Logger.getInstance(AddImportAction.class);

  private final Project myProject;
  private final PsiReference myReference;
  private final PsiClass[] myTargetClasses;
  private final Editor myEditor;

  public AddImportAction(@NotNull Project project,
                         @NotNull PsiReference ref,
                         @NotNull Editor editor,
                         PsiClass @NotNull ... targetClasses) {
    myProject = project;
    myReference = ref;
    myTargetClasses = targetClasses;
    myEditor = editor;
  }

  @RequiresReadLock
  public static @Nullable AddImportAction create(
    @NotNull Editor editor,
    @NotNull Module module,
    @NotNull PsiReference reference,
    @NotNull String className
  ) {
    Project project = module.getProject();
    return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(module);
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
      if (aClass == null) return null;
      return new AddImportAction(project, reference, editor, aClass);
    });
  }

  @Override
  public boolean execute() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (!myReference.getElement().isValid()) {
      return false;
    }

    for (PsiClass myTargetClass : myTargetClasses) {
      if (!myTargetClass.isValid()) {
        return false;
      }
    }

    if (myTargetClasses.length == 1) {
      addImport(myReference, myTargetClasses[0]);
    }
    else {
      chooseClassAndImport();
    }
    return true;
  }

  private void chooseClassAndImport() {
    CodeInsightUtil.sortIdenticalShortNamedMembers(myTargetClasses, myReference);

    class Maps {
      final Map<PsiClass, String> names;
      final Map<PsiClass, Icon> icons;

      Maps(Map<PsiClass, String> names, Map<PsiClass, Icon> icons) {
        this.names = names;
        this.icons = icons;
      }
    }

    Maps maps = ReadAction.compute(() -> {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-346760, EA-1028089")) {
        return new Maps(
          Arrays.stream(myTargetClasses)
            .collect(Collectors.toMap(o -> o, t -> StringUtil.notNullize(t.getQualifiedName()))),
          Arrays.stream(myTargetClasses)
            .collect(Collectors.toMap(o -> o, t -> t.getIcon(0)))
        );
      }
    });

    final BaseListPopupStep<PsiClass> step =
      new BaseListPopupStep<>(QuickFixBundle.message("class.to.import.chooser.title"), myTargetClasses) {

        @Override
        public boolean isAutoSelectionEnabled() {
          return false;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep<?> onChosen(PsiClass selectedValue, boolean finalChoice) {
          if (selectedValue == null) {
            return FINAL_CHOICE;
          }

          if (finalChoice) {
            return doFinalStep(() -> {
              PsiDocumentManager.getInstance(myProject).commitAllDocuments();
              addImport(myReference, selectedValue);
            });
          }

          return getExcludesStep(myProject, selectedValue.getQualifiedName());
        }

        @Override
        public boolean hasSubstep(PsiClass selectedValue) {
          return true;
        }

        @Override
        public @NotNull String getTextFor(PsiClass value) {
          return maps.names.getOrDefault(value, "");
        }

        @Override
        public Icon getIconFor(PsiClass value) {
          return maps.icons.get(value);
        }

        @Override
        public boolean isLazyUiSnapshot() {
          return true;
        }
      };
    JBPopup popup = JBPopupFactory.getInstance().createListPopup(myProject, step, superRenderer -> {
      GroupedItemsListRenderer<Object> baseRenderer = (GroupedItemsListRenderer<Object>)superRenderer;
      ListCellRenderer<Object> psiRenderer = new DefaultPsiElementCellRenderer();
      return (list, value, index, isSelected, cellHasFocus) -> {
        baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        JPanel panel = new JPanel(new BorderLayout()) {
          private final AccessibleContext myAccessibleContext = baseRenderer.getAccessibleContext();

          @Override
          public AccessibleContext getAccessibleContext() {
            if (myAccessibleContext == null) {
              return super.getAccessibleContext();
            }
            return myAccessibleContext;
          }
        };
        panel.add(baseRenderer.getNextStepLabel(), BorderLayout.EAST);
        panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus));
        return panel;
      };
    });

    NavigationUtil.hidePopupIfDumbModeStarts(popup, myProject);
    popup.showInBestPositionFor(myEditor);
  }

  public static @Nullable PopupStep<?> getExcludesStep(@NotNull Project project, @Nullable String qname) {
    if (qname == null) return PopupStep.FINAL_CHOICE;

    List<String> toExclude = getAllExcludableStrings(qname);

    return new BaseListPopupStep<>(null, toExclude) {
      @Override
      public @NotNull String getTextFor(String value) {
        return JavaBundle.message("exclude.0.from.auto.import", value);
      }

      @Override
      public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
        if (finalChoice && selectedValue != null) {
          excludeFromImport(project, selectedValue);
        }

        return super.onChosen(selectedValue, finalChoice);
      }
    };
  }

  public static void excludeFromImport(@NotNull Project project, @NotNull String prefix) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) return;

      final AutoImportOptionsConfigurable configurable = new AutoImportOptionsConfigurable(project);
      ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
        final JavaAutoImportOptions options = ContainerUtil.findInstance(configurable.getConfigurables(), JavaAutoImportOptions.class);
        options.addExcludePackage(prefix);
      });
    });
  }

  public static @NotNull List<String> getAllExcludableStrings(@NotNull String qname) {
    List<String> toExclude = new ArrayList<>();
    while (true) {
      toExclude.add(qname);
      final int i = qname.lastIndexOf('.');
      if (i < 0 || i == qname.indexOf('.')) break;
      qname = qname.substring(0, i);
    }
    return toExclude;
  }

  private void addImport(@NotNull PsiReference ref, @NotNull PsiClass targetClass) {
    DumbService.getInstance(myProject).withAlternativeResolveEnabled(() -> {
      if (!ref.getElement().isValid() || !targetClass.isValid()) {
        return;
      }

      StatisticsManager.getInstance().incUseCount(JavaStatisticsManager.createInfo(null, targetClass));
      WriteCommandAction.runWriteCommandAction(myProject, QuickFixBundle.message("add.import"), null,
                                               () -> doAddImport(ref, targetClass),
                                               ref.getElement().getContainingFile());
    });
  }

  private void doAddImport(@NotNull PsiReference ref, @NotNull PsiClass targetClass) {
    try {
      bindReference(ref, targetClass);
      if (CodeInsightWorkspaceSettings.getInstance(myProject).isOptimizeImportsOnTheFly()) {
        Document document = myEditor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        new OptimizeImportsProcessor(myProject, psiFile).runWithoutProgress();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected void bindReference(@NotNull PsiReference ref, @NotNull PsiClass targetClass) {
    ref.bindToElement(targetClass);
  }
}
