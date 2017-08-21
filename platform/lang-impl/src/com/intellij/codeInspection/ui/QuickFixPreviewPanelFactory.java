/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.ui.actions.suppress.SuppressActionWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixPreviewPanelFactory {
  private static final Logger LOG = Logger.getInstance(QuickFixPreviewPanelFactory.class);
  private static final int MAX_FIX_COUNT = 3;

  @Nullable
  public static JComponent create(@NotNull InspectionResultsView view) {
    if (view.isUpdating() && !view.getTree().areDescriptorNodesSelected()) {
      return new LoadingInProgressPreview(view);
    }
    else {
      final QuickFixReadyPanel panel = new QuickFixReadyPanel(view);
      return panel.isEmpty() ? null : panel;
    }
  }

  private static class QuickFixReadyPanel extends JPanel {
    @NotNull private final InspectionResultsView myView;
    private final InspectionToolWrapper myWrapper;
    private final boolean myEmpty;

    public QuickFixReadyPanel(@NotNull InspectionResultsView view) {
      myView = view;
      myWrapper = view.getTree().getSelectedToolWrapper(true);
      LOG.assertTrue(myWrapper != null);
      CommonProblemDescriptor[] descriptors = myView.getTree().getSelectedDescriptors(false, null, false, true);
      QuickFixAction[] fixes = view.getProvider().getQuickFixes(myWrapper, view.getTree());
      myEmpty = fillPanel(fixes, descriptors, view);
    }

    public boolean isEmpty() {
      return myEmpty;
    }

    private boolean fillPanel(@NotNull QuickFixAction[] fixes,
                              CommonProblemDescriptor[] descriptors,
                              @NotNull InspectionResultsView view) {
      boolean hasFixes = fixes.length != 0;
      int problemCount = descriptors.length;
      boolean multipleDescriptors = problemCount > 1;
      setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
      boolean hasComponents = false;

      final int actualProblemCount = myView.getTree().getContext().getPresentation(myWrapper).getProblemsCount(myView.getTree());
      if (actualProblemCount > 1 || (actualProblemCount == 1 && problemCount > 1)) {
        add(getLabel(actualProblemCount));
        hasComponents = true;
      }

      final DefaultActionGroup actions = new DefaultActionGroup();
      if (hasFixes) {
        actions.addAll(createFixActions(fixes, multipleDescriptors));
      }
      final AnAction suppressionCombo = createSuppressionCombo(myView);
      if (suppressionCombo != null) {
        actions.add(suppressionCombo);
      }
      if (actions.getChildrenCount() != 0) {
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("inspection.view.quick.fix.preview", actions, true);
        final JComponent component = toolbar.getComponent();
        toolbar.setTargetComponent(view);
        add(component);
        hasComponents = true;
      }

      if (hasComponents) {
        int top = hasFixes ? 2 : 9;
        int left = (hasFixes || problemCount > 1) ? 8 : 5;
        int bottom = hasFixes ? 0 : 8;
        setBorder(JBUI.Borders.empty(top, left, bottom, 0));
      }
      return !hasComponents;
    }

    @Nullable
    private static AnAction createSuppressionCombo(InspectionResultsView view) {
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
          getTemplatePresentation().setText("Suppress");
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(JComponent button) {
          DefaultActionGroup group = new DefaultCompactActionGroup();
          group.addAll(availableSuppressors);
          return group;
        }
      };
      action.setSmallVariant(false);
      return action;
    }

    @NotNull
    private static AnAction[] createFixActions(QuickFixAction[] fixes, boolean multipleDescriptors) {
      if (fixes.length > MAX_FIX_COUNT) {
        final ComboBoxAction fixComboBox = new ComboBoxAction() {
          {
            getTemplatePresentation().setText("Apply quick fixes" + (multipleDescriptors ? " to all the problems" : ""));
            getTemplatePresentation().setIcon(AllIcons.Actions.CreateFromUsage);
            setSmallVariant(false);
          }

          @NotNull
          @Override
          protected DefaultActionGroup createPopupActionGroup(JComponent button) {
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


  private static class LoadingInProgressPreview extends JPanel implements InspectionTreeLoadingProgressAware {
    private final InspectionResultsView myView;
    private SimpleColoredComponent myWaitingLabel;

    private LoadingInProgressPreview(InspectionResultsView view) {
      myView = view;
      setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      setBorder(JBUI.Borders.empty(16, 9, 13, 0));
      AsyncProcessIcon waitingIcon = new AsyncProcessIcon("Inspection preview panel updating...");
      Disposer.register(this, waitingIcon);
      myWaitingLabel = getLabel(myView.getTree().getSelectedProblemCount(false));
      add(myWaitingLabel);
      add(waitingIcon);
    }

    @Override
    public void updateLoadingProgress() {
      if (myWaitingLabel != null) {
        myWaitingLabel.clear();
        final InspectionTree tree = myView.getTree();
        appendTextToLabel(myWaitingLabel, tree.getSelectedProblemCount(false));
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

    @Override
    public void dispose() {
    }
  }

  @NotNull
  private static SimpleColoredComponent getLabel(int problemsCount) {
    SimpleColoredComponent label = new SimpleColoredComponent();
    appendTextToLabel(label, problemsCount);
    label.setBorder(JBUI.Borders.empty(0, 0, 0, 2));
    return label;
  }

  private static void appendTextToLabel(SimpleColoredComponent label,
                                        int problemsCount) {
    label.append(problemsCount + " " + StringUtil.pluralize("problem", problemsCount) + ":");
  }
}
