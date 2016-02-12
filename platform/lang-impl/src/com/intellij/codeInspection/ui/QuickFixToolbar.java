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

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalQuickFixWrapper;
import com.intellij.codeInspection.ui.actions.SuppressActionWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.ClickListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixToolbar extends JPanel {
  private static final int MAX_FIX_COUNT = 2;

  @NotNull private final Project myProject;

  public QuickFixToolbar(@NotNull BatchProblemDescriptor descriptor,
                         @NotNull InspectionToolWrapper toolWrapper,
                         @NotNull TreePath[] paths,
                         @NotNull Project project,
                         @Nullable PsiElement containingElement) {

    myProject = project;
    final List<QuickFix> fixes = descriptor.getQuickFixRepresentatives();
    final boolean hasFixes = !fixes.isEmpty();
    final boolean multipleDescriptors = descriptor.getProblemCount() > 1;

    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    List<JPanel> panels = new ArrayList<>();
    for (int i = 0; i < (multipleDescriptors ? 2 : 1); i++) {
      final JPanel line = new JPanel();
      line.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      panels.add((JPanel)add(line));
    }
    if (multipleDescriptors) {
      panels.get(0).setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 0, 0));
    }

    fill(getBulbPlacement(hasFixes), QuickFixToolbar::createBulbIcon, panels);
    fill(getDescriptionLabelPlacement(multipleDescriptors),
         () -> getLabel(hasFixes, (PsiNamedElement)containingElement, descriptor.getProblemCount()), panels);
    fill(getFixesPlacement(hasFixes, multipleDescriptors), () -> createFixPanel(fixes, toolWrapper), panels);
    fill(getSuppressPlacement(multipleDescriptors), () -> createSuppressionCombo(toolWrapper, paths, project), panels);
  }

  @NotNull
  private JLabel getLabel(boolean hasFixes, PsiNamedElement target, int problemsCount) {
    final JBLabel label =
      new JBLabel((hasFixes ? " Fix " : " ") + problemsCount + " warnings " + (target == null ? "" : ("in " + target.getName())));
    Font font = label.getFont();
    if (!hasFixes) {
      label.setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 6, 0));
    }
    label.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
    return label;
  }

  @NotNull
  private static JLabel createBulbIcon() {
    final JLabel label = new JLabel(AllIcons.Actions.IntentionBulb);
    label.setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 0));
    return label;
  }

  private static JComponent createSuppressionCombo(@NotNull final InspectionToolWrapper toolWrapper,
                                                   @NotNull final TreePath[] paths,
                                                   @NotNull final Project project) {
    final ComboBoxAction action = new ComboBoxAction() {
      {
        getTemplatePresentation().setText("Suppress");
      }

      @NotNull
      @Override
      protected ActionGroup createPopupActionGroup(JComponent button) {
        return new SuppressActionWrapper(project, toolWrapper, paths);
      }
    };
    action.setSmallVariant(false);
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  @NotNull
  private static JPanel createFixPanel(List<QuickFix> fixes,
                                       InspectionToolWrapper toolWrapper) {
    final Collection<QuickFix> sortedFixes;
    if (fixes.size() == 1) {
      sortedFixes = fixes;
    }
    else {
      sortedFixes = new TreeSet<>((fix1, fix2) -> {
        int rate1 = 0;
        if (fix1 instanceof HighPriorityAction) rate1 = -1;
        if (fix1 instanceof LowPriorityAction) rate1 = 1;
        int rate2 = 0;
        if (fix2 instanceof HighPriorityAction) rate2 = -1;
        if (fix2 instanceof LowPriorityAction) rate2 = 1;
        if (rate1 != rate2) {
          return rate1 - rate2;
        }
        return fix1.getName().compareTo(fix2.getName());
      });
      sortedFixes.addAll(fixes);
    }
    JPanel fixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(5)));
    if (sortedFixes.size() > MAX_FIX_COUNT) {
      final ComboBoxAction fixComboBox = new ComboBoxAction() {
        {
          getTemplatePresentation().setText("Apply quick fixes");
          setSmallVariant(false);
        }

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(JComponent button) {
          final DefaultActionGroup actionGroup = new DefaultActionGroup();
          for (QuickFix fix : sortedFixes) {
            actionGroup.add(new LocalQuickFixWrapper(fix, toolWrapper));
          }
          return actionGroup;
        }
      };
      fixPanel.add(fixComboBox.createCustomComponent(fixComboBox.getTemplatePresentation()));
    }
    else {
      for (QuickFix fix : sortedFixes) {
        fixPanel.add(createQuickFixButton(fix, toolWrapper));
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

  private static JComponent createQuickFixButton(@NotNull QuickFix fix, @NotNull InspectionToolWrapper toolWrapper) {
    final MyCustomComponentLocalQuickFixWrapper action = new MyCustomComponentLocalQuickFixWrapper(fix, toolWrapper);
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  private static class MyCustomComponentLocalQuickFixWrapper extends LocalQuickFixWrapper implements CustomComponentAction {
    public MyCustomComponentLocalQuickFixWrapper(@NotNull QuickFix fix, @NotNull InspectionToolWrapper toolWrapper) {
      super(fix, toolWrapper);
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      final JButton button = new JButton(presentation.getText());
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
  }
}
