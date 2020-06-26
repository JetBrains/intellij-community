// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.CompositeSettingsBuilder;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.labels.DropDownLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class FragmentedSettingsBuilder<Settings> implements CompositeSettingsBuilder<Settings> {

  private final JPanel myPanel = new JPanel(new GridBagLayout());
  private final GridBagConstraints myConstraints =
    new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(5), 0, 0);
  private final Collection<SettingsEditorFragment<Settings, ?>> myFragments;
  private final SettingsEditorFragment<Settings, ?> myMain;
  private LinkLabel<?> myLinkLabel;

  FragmentedSettingsBuilder(Collection<SettingsEditorFragment<Settings, ?>> fragments, SettingsEditorFragment<Settings, ?> main) {
    myFragments = fragments;
    myMain = main;
  }

  @Override
  public Collection<SettingsEditor<Settings>> getEditors() {
    return new ArrayList<>(myFragments);
  }

  @Override
  public JComponent createCompoundEditor() {
    if (myMain == null) {
      myPanel.setBorder(JBUI.Borders.emptyLeft(5));
      addLine(new JSeparator());
    }
    List<SettingsEditorFragment<Settings, ?>> fragments = new ArrayList<>(myFragments);
    List<SettingsEditorFragment<Settings, ?>> subGroups = ContainerUtil.filter(fragments, fragment -> !fragment.getChildren().isEmpty());
    fragments.removeAll(subGroups);
    fragments.sort(Comparator.comparingInt(SettingsEditorFragment::getCommandLinePosition));
    buildBeforeRun(fragments);
    addLine(buildHeader(fragments));
    if (myMain != null && myMain.component() != null) {
      addLine(myMain.component());
    }
    buildCommandLinePanel(fragments);

    JPanel tagsPanel = new JPanel(new WrapLayout(FlowLayout.LEADING, 0, 0));
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      if (fragment.isTag()) {
        tagsPanel.add(fragment.getComponent());
      }
      else {
        addLine(fragment.getComponent());
      }
    }
    addLine(tagsPanel);

    for (SettingsEditorFragment<Settings, ?> group : subGroups) {
      addLine(group.getComponent());
    }
    if (myMain == null) {
      myConstraints.weighty = 1;
      myPanel.add(new JPanel(), myConstraints);
    }

    List<PanelWithAnchor> panels =
      fragments.stream().map(SettingsEditorFragment::component).filter(component -> component instanceof PanelWithAnchor)
        .map(component -> (PanelWithAnchor)component).collect(Collectors.toList());
    UIUtil.mergeComponentsWithAnchor(panels);
    return myPanel;
  }

  private void addLine(Component component) {
    myPanel.add(component, myConstraints.clone());
    myConstraints.gridy++;
  }

  private void buildBeforeRun(Collection<SettingsEditorFragment<Settings, ?>> fragments) {
    SettingsEditorFragment<Settings, ?> beforeRun = ContainerUtil.find(fragments, fragment -> fragment.getCommandLinePosition() == -2);
    if (beforeRun != null) {
      addLine(beforeRun.getComponent());
      fragments.remove(beforeRun);
    }
  }

  private JComponent buildHeader(Collection<SettingsEditorFragment<Settings, ?>> fragments) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(5, 0));
    SettingsEditorFragment<Settings, ?> label = ContainerUtil.find(fragments, fragment -> fragment.getCommandLinePosition() == -1);
    if (label != null) {
      panel.add(label.getComponent(), BorderLayout.WEST);
      fragments.remove(label);
    }
    if (myMain != null) {
      panel.add(SeparatorFactory.createSeparator(myMain.getGroup(), null), BorderLayout.CENTER);
    }
    myLinkLabel = new DropDownLink<>(OptionsBundle.message(myMain == null? "settings.editor.modify.options" : "settings.editor.modify"), () -> showOptions());
    myLinkLabel.setBorder(JBUI.Borders.emptyLeft(5));
    panel.add(myLinkLabel, BorderLayout.EAST);
    return panel;
  }

  private void showOptions() {
    List<SettingsEditorFragment<Settings, ?>> fragments =
      ContainerUtil.filter(myFragments, fragment -> fragment.getName() != null);
    DefaultActionGroup actionGroup = buildGroup(fragments);
    DataContext dataContext = DataManager.getInstance().getDataContext(myLinkLabel);
    JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("popup.title.add.run.options"),
                                                        actionGroup,
                                                        dataContext,
                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).showUnderneathOf(myLinkLabel);
  }

  @NotNull
  private DefaultActionGroup buildGroup(List<SettingsEditorFragment<Settings, ?>> fragments) {
    fragments.sort(Comparator.comparingInt(SettingsEditorFragment::getMenuPosition));
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    String group = null;
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      if (!Objects.equals(group, fragment.getGroup())) {
        group = fragment.getGroup();
        actionGroup.add(new Separator(group));
      }
      actionGroup.add(new ToggleFragmentAction(fragment));
      List<SettingsEditorFragment<Settings, ?>> children = fragment.getChildren();
      if (!children.isEmpty()) {
        DefaultActionGroup childGroup = buildGroup(children);
        childGroup.setPopup(true);
        childGroup.getTemplatePresentation().setText(fragment.getChildrenGroupName());
        actionGroup.add(childGroup);
      }
    }
    return actionGroup;
  }

  private void buildCommandLinePanel(Collection<SettingsEditorFragment<Settings, ?>> fragments) {
    List<SettingsEditorFragment<Settings, ?>> list = ContainerUtil.filter(fragments, fragment -> fragment.getCommandLinePosition() > 0);
    if (list.isEmpty()) return;
    fragments.removeAll(list);
    CommandLinePanel panel = new CommandLinePanel(ContainerUtil.map(list, fragment -> fragment.createEditor()));
    for (SettingsEditorFragment<Settings, ?> fragment : list) {
      fragment.addSettingsEditorListener(editor -> panel.rebuildRows());
    }
    addLine(panel);
  }

  private static final class ToggleFragmentAction extends ToggleAction {
    private final SettingsEditorFragment<?, ?> myFragment;

    private ToggleFragmentAction(SettingsEditorFragment<?, ?> fragment) {
      super(fragment.getName());
      myFragment = fragment;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFragment.isSelected();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFragment.toggle(state);
    }
  }
}
