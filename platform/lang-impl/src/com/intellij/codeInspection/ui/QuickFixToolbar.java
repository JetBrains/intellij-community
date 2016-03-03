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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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

    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setBorder(IdeBorderFactory.createEmptyBorder(7 + (hasFixes ? 0 : 5), hasFixes ? 12 : 9, hasFixes ? 0 : 6, 0));

    fill(multipleDescriptors, () -> getLabel(fixes, tree.getSelectionCount() == 1 ? (InspectionTreeNode)tree.getSelectionPath().getLastPathComponent() : null, problemCount), this);
    fill(hasFixes, () -> createFixPanel(fixes, multipleDescriptors), this);
    fill(true, () -> createSuppressionCombo(tree.getSelectedToolWrapper(), tree.getSelectionPaths(), project, multipleDescriptors), this);
    fill(multipleDescriptors && editor != null, () -> ActionManager.getInstance().createActionToolbar("", GoToSubsequentOccurrenceAction.createNextPreviousActions(
      editor, descriptors), true).getComponent(), this);
  }

  @NotNull
  private static JComponent getLabel(QuickFixAction[] fixes, InspectionTreeNode targetNode, int problemsCount) {
    final String targetName = targetNode instanceof RefElementNode ? ((RefElementNode)targetNode).getElement().getName() : null;
    SimpleColoredComponent label = new SimpleColoredComponent();
    boolean hasFixesNonIntersectedFixes = fixes != null && fixes.length == 0;
    label.append(problemsCount + " problems" +
                 (targetName == null ? "" : (" in " + targetName)) +
                 (problemsCount > 1 && (fixes != null && fixes.length == MAX_FIX_COUNT) ? "    Fix all:" : "") +
                 (hasFixesNonIntersectedFixes ? ":" : "" ));
    if (hasFixesNonIntersectedFixes) {
      label.append(" select a single problem to see its quick fixes");
    }
    label.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 0, 2));
    return label;
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
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  @NotNull
  private static JPanel createFixPanel(QuickFixAction[] fixes, boolean multipleDescriptors) {
    JPanel fixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(5)));
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
      fixPanel.add(fixComboBox.createCustomComponent(fixComboBox.getTemplatePresentation()));
    }
    else {
      final boolean multipleFixes = fixes.length > 1;
      for (QuickFixAction fix : fixes) {
        fixPanel.add(createQuickFixButton(fix, multipleDescriptors && !multipleFixes));
      }
    }
    return fixPanel;
  }

  private static void fill(boolean add,
                           @NotNull Supplier<JComponent> componentSupplier,
                           @NotNull JPanel parent) {
    if (add) {
      parent.add(componentSupplier.get());
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
