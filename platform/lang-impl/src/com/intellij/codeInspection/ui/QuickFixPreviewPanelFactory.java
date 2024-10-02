// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.ui.actions.suppress.SuppressActionWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.SlowOperations;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
public final class QuickFixPreviewPanelFactory {
  private static final Logger LOG = Logger.getInstance(QuickFixPreviewPanelFactory.class);
  private static final int MAX_FIX_COUNT = 3;

  public static @Nullable JComponent create(@NotNull InspectionResultsView view) {
    if (view.isUpdating() && !view.getTree().areDescriptorNodesSelected()) {
      return new LoadingInProgressPreview(view);
    }
    else {
      final QuickFixReadyPanel panel = new QuickFixReadyPanel(view);
      return panel.isEmpty() ? null : panel;
    }
  }

  private static final class QuickFixReadyPanel extends JPanel {
    private final @NotNull InspectionResultsView myView;
    private final InspectionToolWrapper myWrapper;
    private final boolean myEmpty;

    QuickFixReadyPanel(@NotNull InspectionResultsView view) {
      myView = view;
      InspectionTree tree = view.getTree();
      myWrapper = tree.getSelectedToolWrapper(true);
      LOG.assertTrue(myWrapper != null);
      QuickFixAction[] commonFixes = view.getProvider().getCommonQuickFixes(myWrapper,
                                                                            tree,
                                                                            tree.getSelectedDescriptors(),
                                                                            tree.getSelectedElements());
      boolean multipleDescriptors = myView.getTree().getSelectedDescriptors().length > 1;
      QuickFixAction[] partialFixes = QuickFixAction.EMPTY;
      if (multipleDescriptors && commonFixes.length == 0) {
        partialFixes = view.getProvider().getPartialQuickFixes(myWrapper, tree, tree.getSelectedDescriptors());
      }
      try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162775")) {
        myEmpty = fillPanel(commonFixes, partialFixes, multipleDescriptors, view);
      }
    }

    public boolean isEmpty() {
      return myEmpty;
    }

    private boolean fillPanel(QuickFixAction @NotNull [] fixes,
                              QuickFixAction @NotNull [] partialFixes,
                              boolean multipleDescriptors,
                              @NotNull InspectionResultsView view) {
      boolean hasFixes = fixes.length != 0;
      setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      boolean hasComponents = false;

      InspectionTree tree = myView.getTree();
      InspectionToolPresentation presentation = tree.getContext().getPresentation(myWrapper);
      final boolean showProblemCount = presentation.showProblemCount();

      if (showProblemCount) {
        final int actualProblemCount = tree.getSelectedProblemCount();
        if (actualProblemCount > 1 || (actualProblemCount == 1 && multipleDescriptors)) {
          add(getLabel(actualProblemCount));
          hasComponents = true;
        }
      }

      final DefaultActionGroup actions = new DefaultActionGroup();
      if (hasFixes) {
        actions.addAll(createFixActions(fixes, multipleDescriptors));
      }
      final AnAction suppressionCombo = createSuppressionCombo(myView);
      if (suppressionCombo != null) {
        actions.add(suppressionCombo);
      }

      if (partialFixes.length != 0) {
        actions.add(createPartialFixCombo(partialFixes));
      }

      if (actions.getChildrenCount() != 0) {
        view.setFixesAvailable(true);
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("inspection.view.quick.fix.preview", actions, true);
        final JComponent component = toolbar.getComponent();
        toolbar.setTargetComponent(view);
        add(component);
        hasComponents = true;
      }

      if (hasComponents) {
        int top = hasFixes ? 2 : 9;
        int left = (hasFixes || multipleDescriptors) ? 8 : 5;
        int bottom = hasFixes ? 0 : 8;
        setBorder(JBUI.Borders.empty(top, left, bottom, 0));
      }

      return !hasComponents;
    }

    private static @NotNull AnAction createPartialFixCombo(QuickFixAction[] fixes) {
      DefaultActionGroup group = new DefaultActionGroup();
      for (QuickFixAction fix : fixes) {
        group.add(fix);
      }

      return new ComboBoxAction() {
        {
          getTemplatePresentation().setText(CodeInsightBundle.messagePointer("action.presentation.QuickFixPreviewPanelFactory.text"));
          setSmallVariant(false);
        }

        @Override
        protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
          return group;
        }
      };
    }

    private static @Nullable AnAction createSuppressionCombo(InspectionResultsView view) {
      final AnActionEvent
        event = AnActionEvent.createFromDataContext(ActionPlaces.CODE_INSPECTION, null, DataManager.getInstance().getDataContext(view));
      final AnAction[] suppressors = new SuppressActionWrapper().getChildren(event);
      final AnAction[] availableSuppressors = Arrays.stream(suppressors).filter(s -> {
        event.getPresentation().setEnabled(false);
        s.update(event);
        return event.getPresentation().isEnabled();
      }).toArray(AnAction[]::new);
      if (availableSuppressors.length == 0) {
        return null;
      }
      final ComboBoxAction action = new ComboBoxAction() {
        {
          getTemplatePresentation().setText(CodeInsightBundle.messagePointer("action.presentation.QuickFixPreviewPanelFactory.text.suppress"));
        }

        @Override
        protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
          DefaultActionGroup group = new DefaultCompactActionGroup();
          group.addAll(availableSuppressors);
          return group;
        }
      };
      action.setSmallVariant(false);
      return action;
    }

    private static AnAction @NotNull [] createFixActions(QuickFixAction[] fixes, boolean multipleDescriptors) {
      if (fixes.length > MAX_FIX_COUNT) {
        final ComboBoxAction fixComboBox = new ComboBoxAction() {
          {
            if (multipleDescriptors) {
              getTemplatePresentation().setText(InspectionsBundle.message("apply.quick.fixes.to.all.action.text"));
            }
            else {
              getTemplatePresentation().setText(InspectionsBundle.message("apply.quick.fixes.action.text"));
            }
            getTemplatePresentation().setIcon(AllIcons.Actions.IntentionBulb);
            setSmallVariant(false);
          }

          @Override
          protected @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
            final DefaultActionGroup actionGroup = new DefaultActionGroup();
            for (QuickFixAction fix : fixes) {
              actionGroup.add(fix);
            }
            return actionGroup;
          }
        };
        return new AnAction[] {fixComboBox};
      }
      return fixes;
    }

  }


  private static final class LoadingInProgressPreview extends JPanel implements InspectionTreeLoadingProgressAware {
    private final InspectionResultsView myView;
    private final SimpleColoredComponent myWaitingLabel;

    private LoadingInProgressPreview(InspectionResultsView view) {
      myView = view;
      setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      setBorder(JBUI.Borders.empty(16, 9, 13, 0));
      AsyncProcessIcon waitingIcon = new AsyncProcessIcon("Inspection preview panel updating...");
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          waitingIcon.dispose();
        }
      });
      myWaitingLabel = getLabel(myView.getTree() .getSelectedProblemCount());
      add(myWaitingLabel);
      add(waitingIcon);
    }

    @Override
    public void updateLoadingProgress() {
      if (myWaitingLabel != null) {
        myWaitingLabel.clear();
        final InspectionTree tree = myView.getTree();
        appendTextToLabel(myWaitingLabel, tree.getSelectedProblemCount());
      }
    }

    @Override
    public void treeLoaded() {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myView.isDisposed()) {
          myView.syncRightPanel();
        }
      });
    }
  }

  private static @NotNull SimpleColoredComponent getLabel(int problemsCount) {
    SimpleColoredComponent label = new SimpleColoredComponent();
    appendTextToLabel(label, problemsCount);
    label.setBorder(JBUI.Borders.emptyRight(2));
    return label;
  }

  private static void appendTextToLabel(SimpleColoredComponent label, int problemsCount) {
    label.append(LangBundle.message("label.n.problems", problemsCount));
  }
}
