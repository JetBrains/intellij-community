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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ClickListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

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
    setBorder(IdeBorderFactory.createEmptyBorder(2, 8, 0, 0));
    myTargetName = getTargetName();

    if (view.isUpdating() && !areDescriptorNodesSelected()) {
      AsyncProcessIcon waitingIcon = new AsyncProcessIcon("Inspection preview panel updating...");
      Disposer.register(this, waitingIcon);
      myWaitingLabel = getLabel(null, problemCount);
      add(myWaitingLabel);
      add(waitingIcon);
    }
    else {
      QuickFixAction[] fixes = view.getProvider().getQuickFixes(myWrapper, view.getTree());
      fillPanel(fixes, descriptors);
    }
  }

  @Override
  public void treeLoaded() {
    if (myWaitingLabel != null) {
      removeAll();
      final InspectionTree tree = myView.getTree();
      QuickFixAction[] fixes = myView.getProvider().getQuickFixes(myWrapper, tree);
      CommonProblemDescriptor[] descriptors = tree.getSelectedDescriptors();
      fillPanel(fixes, descriptors);
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
    fill(multipleDescriptors, () -> getLabel(fixes, problemCount), this);
    fill(hasFixes, () -> createFixPanel(fixes, multipleDescriptors), this);
    fill(true, () -> createSuppressionCombo(myWrapper, tree.getSelectionPaths(), project, multipleDescriptors), this);
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

  private static JComponent createSuppressionCombo(@NotNull final InspectionToolWrapper toolWrapper,
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
    return fixComboBoxActionComponent(action.createCustomComponent(action.getTemplatePresentation()));
  }

  private static JComponent fixComboBoxActionComponent(JComponent component) {
    JPanel containingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    containingPanel.add(component);
    containingPanel.setBorder(IdeBorderFactory.createEmptyBorder(7, 0, 8, 0));
    return containingPanel;
  }

  private static void fill(boolean add,
                           @NotNull Supplier<JComponent> componentSupplier,
                           @NotNull JPanel parent) {
    if (add) {
      parent.add(componentSupplier.get());
    }
  }

  @NotNull
  private static JComponent createFixPanel(QuickFixAction[] fixes, boolean multipleDescriptors) {
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
      return fixComboBoxActionComponent(fixComboBox.createCustomComponent(fixComboBox.getTemplatePresentation()));
    }
    else {
      JPanel fixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      final boolean multipleFixes = fixes.length > 1;
      for (QuickFixAction fix : fixes) {
        fixPanel.add(createQuickFixButton(fix, multipleDescriptors && !multipleFixes));
      }
      return fixPanel;
    }
  }

  private static JComponent createQuickFixButton(@NotNull QuickFixAction fix, boolean multipleFixes) {
    final MyCustomComponentLocalQuickFixWrapper action = new MyCustomComponentLocalQuickFixWrapper(fix);
    if (multipleFixes) {
      action.getTemplatePresentation().setText("Fix all '" + fix.getText() + "'");
    }
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  private static class MyCustomComponentLocalQuickFixWrapper extends AnAction implements CustomComponentAction {
    private QuickFixAction myUnderlying;

    public MyCustomComponentLocalQuickFixWrapper(@NotNull QuickFixAction underlying) {
      myUnderlying = underlying;
      copyFrom(underlying);
    }


    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      final JButton button = new JButton(presentation.getText());
      button.setIcon(presentation.getIcon());
      if (presentation.getIcon() == null) {
        button.setIcon(AllIcons.Actions.CreateFromUsage);
      }
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          actionPerformed(AnActionEvent.createFromAnAction(MyCustomComponentLocalQuickFixWrapper.this,
                                                           event,
                                                           "LOCAL_QUICK_FIX_WRAPPER_PANEL",
                                                           DataManager.getInstance().getDataContext(button)));
          return true;
        }
      }.installOn(button);
      return button;
    }


    @Override
    public void update(AnActionEvent e) {
      myUnderlying.update(e);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myUnderlying.actionPerformed(e);
    }
  }
}
