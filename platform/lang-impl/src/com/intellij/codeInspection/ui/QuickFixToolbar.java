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
import com.intellij.codeInspection.ui.actions.occurrences.GoToSubsequentOccurrenceAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ClickListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixToolbar extends JPanel {
  private static final int MAX_FIX_COUNT = 2;

  public QuickFixToolbar(@NotNull InspectionTree tree,
                         @NotNull Project project,
                         @Nullable Editor editor,
                         @Nullable QuickFixAction[] fixes) {
    final boolean hasFixes = fixes != null && fixes.length != 0;
    CommonProblemDescriptor[] descriptors = tree.getSelectedDescriptors();
    int problemCount = descriptors.length;
    final boolean multipleDescriptors = problemCount > 1;

    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    List<JPanel> panels = new ArrayList<>();
    for (int i = 0; i < (multipleDescriptors ? 2 : 1); i++) {
      final JPanel line = new JPanel();
      line.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      panels.add((JPanel)add(line));
    }
    if (multipleDescriptors || !hasFixes) {
      panels.get(0).setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 0, 0));
    }

    //fill(getBulbPlacement(hasFixes), QuickFixToolbar::createBulbIcon, panels);
    fill(getDescriptionLabelPlacement(multipleDescriptors),
         () -> getLabel(fixes, tree.getSelectionCount() == 1 ? (InspectionTreeNode)tree.getSelectionPath().getLastPathComponent() : null, problemCount), panels);
    fill(getFixesPlacement(hasFixes, multipleDescriptors), () -> createFixPanel(fixes), panels);
    fill(getSuppressPlacement(multipleDescriptors), () -> createSuppressionCombo(tree.getSelectedToolWrapper()
      , tree.getSelectionPath(), project), panels);
    fill(multipleDescriptors && editor != null ? 1 : -1, () -> ActionManager.getInstance().createActionToolbar("", GoToSubsequentOccurrenceAction.createNextPreviousActions(
      editor, descriptors), true).getComponent(), panels);
  }

  @NotNull
  private static JComponent getLabel(QuickFixAction[] fixes, InspectionTreeNode targetNode, int problemsCount) {
    final String targetName = targetNode instanceof RefElementNode ? ((RefElementNode)targetNode).getElement().getName() : null;
    SimpleColoredComponent label = new SimpleColoredComponent();
    boolean hasFixesNonIntersectedFixes = fixes != null && fixes.length == 0;
    boolean hasFixes = fixes != null && fixes.length != 0;
    label.append((hasFixes ? " Fix " : " ") + problemsCount + " problems" + (targetName == null ? "" : (" in " + targetName)) + (
      hasFixesNonIntersectedFixes
      ? ":" : ""), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (hasFixesNonIntersectedFixes) {
      label.append(" select a single problem to see its quick fixes");
    }

    if (!hasFixes) {
      label.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 6, 0));
    }
    return label;
  }

  @NotNull
  private static JLabel createBulbIcon() {
    final JLabel label = new JLabel(AllIcons.Actions.IntentionBulb);
    label.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 0));
    return label;
  }

  private static JComponent createSuppressionCombo(@NotNull final InspectionToolWrapper toolWrapper,
                                                   @NotNull final TreePath path,
                                                   @NotNull final Project project) {
    final ComboBoxAction action = new ComboBoxAction() {
      {
        getTemplatePresentation().setText("Suppress");
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(new SuppressActionWrapper(project, toolWrapper, path).getChildren(null));
        return group;
      }
    };
    action.setSmallVariant(false);
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  @NotNull
  private static JPanel createFixPanel(QuickFixAction[] fixes) {
    JPanel fixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(5)));
    if (fixes.length > MAX_FIX_COUNT) {
      final ComboBoxAction fixComboBox = new ComboBoxAction() {
        {
          getTemplatePresentation().setText("Apply quick fixes");
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
      fixPanel.add(fixComboBox.createCustomComponent(fixComboBox.getTemplatePresentation()));
    }
    else {
      for (QuickFixAction fix : fixes) {
        fixPanel.add(createQuickFixButton(fix));
      }
    }
    return fixPanel;
  }

  private static void fill(int row,
                           @NotNull Supplier<JComponent> componentSupplier,
                           @NotNull List<JPanel> parent) {
    if (row == -1) {
      return;
    }
    final JPanel rowPanel = parent.get(row);
    rowPanel.add(componentSupplier.get());
  }

  private static int getSuppressPlacement(boolean multipleDescriptors) {
    return multipleDescriptors ? 1 : 0;
  }

  private static int getFixesPlacement(boolean hasQuickFixes, boolean multipleDescriptors) {
    return hasQuickFixes ? multipleDescriptors ? 1 : 0 : -1;
  }

  private static int getDescriptionLabelPlacement(boolean multipleDescriptors) {
    return multipleDescriptors ? 0 : -1;
  }

  private static int getBulbPlacement(boolean hasQuickFixes) {
    return hasQuickFixes ? 0 : -1;
  }

  private static JComponent createQuickFixButton(@NotNull QuickFixAction fix) {
    final MyCustomComponentLocalQuickFixWrapper action = new MyCustomComponentLocalQuickFixWrapper(fix);
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
