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

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.actions.SuppressActionWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
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
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixToolbar extends JPanel {
  @NotNull private final Project myProject;

  public QuickFixToolbar(@NotNull BatchProblemDescriptor descriptor,
                         @NotNull InspectionToolWrapper toolWrapper,
                         @NotNull TreePath[] paths,
                         @NotNull Project project,
                         @Nullable PsiElement containingElement) {

    myProject = project;
    final Set<String> fixes = descriptor.getQuickFixNames();
    final boolean hasFixes = fixes != null && !fixes.isEmpty();
    final boolean multipleDescriptors = descriptor.getProblemCount() > 1;

    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    List<JPanel> panels = new ArrayList<>();
    for (int i = 0; i < (multipleDescriptors ? 2 : 1); i++) {
      final JPanel line = new JPanel();
      line.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      panels.add((JPanel)add(line));
    }
    if (multipleDescriptors) {
      panels.get(0).setBorder(IdeBorderFactory.createEmptyBorder(new Insets(JBUI.scale(5), 0, 0, 0)));
    }

    fill(getBulbPlacement(hasFixes), QuickFixToolbar::createBulbIcon, panels);
    fill(getDescriptionLabelPlacement(hasFixes, multipleDescriptors),
         () -> getLabel(hasFixes, (PsiNamedElement)containingElement, descriptor.getProblemCount()), panels);
    fill(getFixesPlacement(hasFixes, multipleDescriptors), () -> createFixPanel(descriptor, project, fixes), panels);
    fill(getSuppressPlacement(hasFixes, multipleDescriptors), () -> createSuppressionCombo(toolWrapper, paths, project), panels);
  }

  @NotNull
  private JLabel getLabel(boolean hasFixes, PsiNamedElement target, int problemsCount) {
    final JBLabel label =
      new JBLabel((hasFixes ? " Fix " : " ") + problemsCount + " warnings " + (target == null ? "" : ("in " + target.getName())));
    Font font = label.getFont();
    if (!hasFixes) {
      label.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(JBUI.scale(0), JBUI.scale(3), JBUI.scale(6), 0)));
    }
    label.setFont(new Font(font.getFontName(), Font.BOLD, font.getSize()));
    return label;
  }

  @NotNull
  private static JLabel createBulbIcon() {
    final JLabel label = new JLabel(AllIcons.Actions.IntentionBulb);
    label.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(0, JBUI.scale(10), 0, 0)));
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
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(new SuppressActionWrapper(project, toolWrapper, paths).getChildren(null));
        return group;
      }
    };
    action.setSmallVariant(false);
    return action.createCustomComponent(action.getTemplatePresentation());
  }

  @NotNull
  private JPanel createFixPanel(@NotNull final BatchProblemDescriptor descriptor, @NotNull final Project project, Set<String> fixes) {
    JPanel fixPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(5)));
    for (String fixName : fixes) {
      final JButton quickFixButton = new JButton(fixName);
      fixPanel.add(quickFixButton);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent event, int clickCount) {
          WriteCommandAction.runWriteCommandAction(myProject, () -> {
            descriptor.applyFixes(fixName, project);
          });
          setVisible(false);
          return true;
        }
      }.installOn(quickFixButton);
    }
    return fixPanel;
  }

  private static void fill(@Nullable Couple<Integer> point,
                           @NotNull Supplier<JComponent> componentSupplier,
                           @NotNull List<JPanel> parent) {
    if (point == null) {
      return;
    }
    final int y = point.getSecond();
    final JPanel row = parent.get(y);
    row.add(componentSupplier.get());
  }

  @NotNull
  private static Couple<Integer> getSuppressPlacement(boolean hasQuickFixes, boolean multipleDescriptors) {
    return hasQuickFixes
           ? multipleDescriptors ? Couple.of(1, 1) : Couple.of(2, 0)
           : Couple.of(0, multipleDescriptors ? 1 : 0);
  }

  @Nullable
  private static Couple<Integer> getFixesPlacement(boolean hasQuickFixes, boolean multipleDescriptors) {
    return hasQuickFixes ? multipleDescriptors ? Couple.of(0, 1) : Couple.of(1, 0) : null;
  }

  @Nullable
  private static Couple<Integer> getDescriptionLabelPlacement(boolean hasQuickFixes, boolean multipleDescriptors) {
    return multipleDescriptors ? Couple.of(hasQuickFixes ? 1 : 0, 0) : null;
  }

  @Nullable
  private static Couple<Integer> getBulbPlacement(boolean hasQuickFixes) {
    return hasQuickFixes ? Couple.of(0, 0) : null;
  }
}
