/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.ui.actions.SuppressActionWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixToolbar extends JPanel implements InspectionTreeLoadingProgressAware {
  private static final Logger LOG = Logger.getInstance(QuickFixToolbar.class);
  private static final int MAX_FIX_COUNT = 3;
  @Nullable private final String myTargetName;
  @NotNull private final InspectionResultsView myView;
  private final InspectionToolWrapper myWrapper;
  private final ProblemPreviewEditorFoldings myFoldings;

  private SimpleColoredComponent myWaitingLabel;

  public QuickFixToolbar(@Nullable EditorEx editor,
                         @NotNull InspectionResultsView view) {
    myFoldings = editor == null ? null : new ProblemPreviewEditorFoldings(editor);
    myView = view;
    myWrapper = view.getTree().getSelectedToolWrapper();
    LOG.assertTrue(myWrapper != null);
    CommonProblemDescriptor[] descriptors = myView.getTree().getSelectedDescriptors();
    int problemCount = descriptors.length;

    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myTargetName = getTargetName();

    if (view.isUpdating() && !areDescriptorNodesSelected()) {
      setBorder(IdeBorderFactory.createEmptyBorder(16, 9, 13, 0));
      AsyncProcessIcon waitingIcon = new AsyncProcessIcon("Inspection preview panel updating...");
      Disposer.register(this, waitingIcon);
      myWaitingLabel = getLabel(null, problemCount);
      add(myWaitingLabel);
      add(waitingIcon);
    }
    else {
      setBorder(IdeBorderFactory.createEmptyBorder(2, 8, 0, 0));
      QuickFixAction[] fixes = view.getProvider().getQuickFixes(myWrapper, view.getTree());
      fillPanel(fixes, descriptors);
    }
  }

  @Override
  public void treeLoaded() {
    if (myWaitingLabel != null) {
      removeAll();
      setBorder(IdeBorderFactory.createEmptyBorder(2, 8, 0, 0));
      final InspectionTree tree = myView.getTree();
      QuickFixAction[] fixes = myView.getProvider().getQuickFixes(myWrapper, tree);
      CommonProblemDescriptor[] descriptors = tree.getSelectedDescriptors();
      fillPanel(fixes, descriptors);
      revalidate();
      repaint();
    }
  }

  @Override
  public void updateLoadingProgress() {
    if (myWaitingLabel != null) {
      myWaitingLabel.clear();
      final InspectionTree tree = myView.getTree();
      appendTextToLabel(myWaitingLabel, tree.getSelectedProblemCount(), null);
    }
  }

  private void fillPanel(@Nullable QuickFixAction[] fixes,
                         CommonProblemDescriptor[] descriptors) {
    if (myFoldings != null) myFoldings.appendFoldings(descriptors);
    InspectionTree tree = myView.getTree();
    Project project = myView.getProject();
    boolean hasFixes = fixes != null && fixes.length != 0;
    int problemCount = descriptors.length;
    boolean multipleDescriptors = problemCount > 1;
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    if (multipleDescriptors) {
      add(getLabel(fixes, problemCount));
    }

    final DefaultActionGroup actions = new DefaultActionGroup();
    if (hasFixes) {
      actions.addAll(createFixActions(fixes, multipleDescriptors));
    }
    actions.add(createSuppressionCombo(myWrapper, tree.getSelectionPaths(), project, multipleDescriptors));
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
    final JComponent component = toolbar.getComponent();
    toolbar.setTargetComponent(this);
    add(component);
  }

  @Nullable
  private String getTargetName() {
    if (myView.getTree().getSelectionCount() == 1) {
      final Object node = myView.getTree().getSelectionPath().getLastPathComponent();
      return node instanceof RefElementNode ? ((RefElementNode)node).getElement().getName() : null;
    }
    return null;
  }

  private boolean areDescriptorNodesSelected() {
    final TreePath[] paths = myView.getTree().getSelectionPaths();
    for (TreePath path : paths) {
      if (!(path.getLastPathComponent() instanceof ProblemDescriptionNode)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private SimpleColoredComponent getLabel(QuickFixAction[] fixes, int problemsCount) {
    SimpleColoredComponent label = new SimpleColoredComponent();
    appendTextToLabel(label, problemsCount, fixes);
    label.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 0, 2));
    return label;
  }

  private void appendTextToLabel(SimpleColoredComponent label,
                                 int problemsCount,
                                 QuickFixAction[] fixes) {
    boolean hasFixesNonIntersectedFixes = fixes != null && fixes.length == 0;
    label.append(problemsCount + " problems" +
                 (myTargetName == null ? "" : (" in " + myTargetName)) +
                 (problemsCount > 1 && (fixes != null && fixes.length >= MAX_FIX_COUNT) ? "    Fix all:" : "") +
                 (!hasFixesNonIntersectedFixes ? ":" : ""));
    if (hasFixesNonIntersectedFixes) {
      label.append(" select a single problem to see its quick fixes");
    }
  }

  private static AnAction createSuppressionCombo(@NotNull final InspectionToolWrapper toolWrapper,
                                                   @NotNull final TreePath[] paths,
                                                   @NotNull final Project project,
                                                   boolean multipleDescriptors) {
    final AnAction[] suppressors = new SuppressActionWrapper(project, toolWrapper, paths).getChildren(null);
    final ComboBoxAction action = new ComboBoxAction() {
      {
        getTemplatePresentation().setText(multipleDescriptors ? "Suppress All" : "Suppress");
        getTemplatePresentation().setEnabledAndVisible(suppressors.length != 0);
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(suppressors);
        return group;
      }
    };
    action.setSmallVariant(false);
    return action;
  }

  @NotNull
  private static List<AnAction> createFixActions(QuickFixAction[] fixes, boolean multipleDescriptors) {
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
      return Collections.singletonList(fixComboBox);
    }
    else {
      final DefaultActionGroup group = new DefaultActionGroup();
      final boolean multipleFixes = fixes.length > 1;
      for (QuickFixAction fix : fixes) {
        final AnAction quickFixButtonCustonPresentation = createQuickFixButton(fix, multipleDescriptors && !multipleFixes);
        //group.addAll(fixes);
        group.add(quickFixButtonCustonPresentation);
      }
      return ContainerUtil.list(fixes);
    }
  }

  private static AnAction createQuickFixButton(@NotNull QuickFixAction fix, boolean multipleFixes) {
    if (multipleFixes) {
      fix.getTemplatePresentation().setText("Fix all '" + fix.getText() + "'");
    }
    return fix;
  }
}
