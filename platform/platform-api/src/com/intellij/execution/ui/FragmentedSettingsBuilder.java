// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.CompositeSettingsBuilder;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.DropDownLink;
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

  static final int TOP_INSET = 5;
  static final int GROUP_INSET = 20;
  public static final int TAG_VGAP = JBUI.scale(6);
  public static final int TAG_HGAP = JBUI.scale(2);

  private final JPanel myPanel = new JPanel(new GridBagLayout()) {
    @Override
    public void addNotify() {
      super.addNotify();
      registerShortcuts();
    }
  };
  private final GridBagConstraints myConstraints =
    new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(TOP_INSET), 0, 0);
  private final Collection<SettingsEditorFragment<Settings, ?>> myFragments;
  private final SettingsEditorFragment<Settings, ?> myMain;
  private DropDownLink<String> myLinkLabel;

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

    JPanel tagsPanel = new JPanel(new WrapLayout(FlowLayout.LEADING, TAG_HGAP, TAG_VGAP));
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      JComponent component = fragment.getComponent();
      if (fragment.isTag()) {
        tagsPanel.add(component);
      }
      else {
        addLine(component);
        if (fragment.getHintComponent() != null) {
          addLine(fragment.getHintComponent(), 0, getLeftInset(component), TOP_INSET);
        }
      }
    }
    addLine(tagsPanel, GROUP_INSET, -getLeftInset((JComponent)tagsPanel.getComponent(0)) - TAG_HGAP, 0);

    for (SettingsEditorFragment<Settings, ?> group : subGroups) {
      addLine(group.getComponent());
    }
    if (myMain == null) {
      myConstraints.weighty = 1;
      myPanel.add(new JPanel(), myConstraints);
    }

    List<PanelWithAnchor> panels = Arrays.stream(myPanel.getComponents()).filter(component -> component instanceof PanelWithAnchor)
        .map(component -> (PanelWithAnchor)component).collect(Collectors.toList());
    UIUtil.mergeComponentsWithAnchor(panels);
    return myPanel;
  }

  private void addLine(Component component, int top, int left, int bottom) {
    myConstraints.insets = JBUI.insets(top, left, bottom, 0);
    myPanel.add(component, myConstraints.clone());
    myConstraints.gridy++;
    myConstraints.insets = JBUI.insetsTop(top);
  }

  private void addLine(Component component) {
    addLine(component, TOP_INSET, 0, 0);
  }

  private void buildBeforeRun(Collection<SettingsEditorFragment<Settings, ?>> fragments) {
    SettingsEditorFragment<Settings, ?> beforeRun = ContainerUtil.find(fragments, fragment -> fragment.getCommandLinePosition() == -2);
    if (beforeRun != null) {
      addLine(beforeRun.getComponent(), TOP_INSET, 0, TOP_INSET * 2);
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
    String message = OptionsBundle.message(myMain == null ? "settings.editor.modify.options" : "settings.editor.modify");
    myLinkLabel = new DropDownLink<>(message, link -> showOptions());
    myLinkLabel.setBorder(JBUI.Borders.emptyLeft(5));
    JPanel linkPanel = new JPanel(new BorderLayout());
    linkPanel.add(myLinkLabel, BorderLayout.CENTER);
    CustomShortcutSet shortcut = KeymapUtil.getMnemonicAsShortcut(TextWithMnemonic.parse(message).getMnemonic());
    if (shortcut != null) {
      JLabel shortcutLabel = new JLabel(KeymapUtil.getFirstKeyboardShortcutText(shortcut));
      shortcutLabel.setEnabled(false);
      shortcutLabel.setBorder(JBUI.Borders.empty(0, 5));
      linkPanel.add(shortcutLabel, BorderLayout.EAST);
    }
    panel.add(linkPanel, BorderLayout.EAST);
    return panel;
  }

  private void registerShortcuts() {
    for (AnAction action : buildGroup().getChildActionsOrStubs()) {
      ShortcutSet shortcutSet = ActionUtil.getMnemonicAsShortcut(action);
      if (shortcutSet != null && action instanceof ToggleFragmentAction) {
        action.registerCustomShortcutSet(shortcutSet, null);
        new AnAction(action.getTemplateText()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            ((ToggleFragmentAction)action).myFragment.toggle(true); // show or set focus
          }
        }.registerCustomShortcutSet(shortcutSet, myPanel.getRootPane());
      }
    }
  }

  private JBPopup showOptions() {
    DataContext dataContext = DataManager.getInstance().getDataContext(myLinkLabel);
    DefaultActionGroup group = buildGroup();
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("popup.title.add.run.options"),
                                                                          group,
                                                                          dataContext,
                                                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
    popup.addListSelectionListener(e -> {
      AnActionHolder data = (AnActionHolder)PlatformDataKeys.SELECTED_ITEM.getData((DataProvider)e.getSource());
      popup.setAdText(getHint(data == null ? null : data.getAction()), SwingConstants.LEFT);
    });
    popup.setAdText(getHint(ContainerUtil.find(group.getChildren(null), action -> !(action instanceof Separator))), SwingConstants.LEFT);
    return popup;
  }

  @NotNull
  private static @NlsContexts.PopupAdvertisement String getHint(AnAction action) {
    if (action == null || action.getTemplatePresentation().getDescription() == null) {
      return IdeBundle.message("popup.advertisement.hover.item.to.see.hint");
    }
    return action.getTemplatePresentation().getDescription();
  }

  @NotNull
  private DefaultActionGroup buildGroup(List<SettingsEditorFragment<Settings, ?>> fragments) {
    fragments.sort(Comparator.comparingInt(SettingsEditorFragment::getMenuPosition));
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    String group = null;
    for (SettingsEditorFragment<Settings, ?> fragment : restoreGroups(fragments)) {
      if (fragment.isRemovable() && !Objects.equals(group, fragment.getGroup())) {
        group = fragment.getGroup();
        actionGroup.add(new Separator(group));
      }
      ActionGroup customGroup = fragment.getCustomActionGroup();
      if (customGroup != null) {
        actionGroup.add(customGroup);
        continue;
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

  private DefaultActionGroup buildGroup() {
    return buildGroup(ContainerUtil.filter(myFragments, fragment -> fragment.getName() != null));
  }

  private List<SettingsEditorFragment<Settings, ?>> restoreGroups(List<SettingsEditorFragment<Settings, ?>> fragments) {
    ArrayList<SettingsEditorFragment<Settings, ?>> result = new ArrayList<>();
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      String group = fragment.getGroup();
      int last = ContainerUtil.lastIndexOf(result, f -> f.getGroup() == group);
      result.add(last >= 0 ? last + 1 : result.size(), fragment);
    }
    return result;
  }

  private void buildCommandLinePanel(Collection<SettingsEditorFragment<Settings, ?>> fragments) {
    List<SettingsEditorFragment<Settings, ?>> list = ContainerUtil.filter(fragments, fragment -> fragment.getCommandLinePosition() > 0);
    if (list.isEmpty()) return;
    fragments.removeAll(list);
    CommandLinePanel panel = new CommandLinePanel(list);
    addLine(panel, 0, -panel.getLeftInset(), TOP_INSET);
  }

  static int getLeftInset(JComponent component) {
    if (component.getBorder() != null) {
      return component.getBorder().getBorderInsets(component).left;
    }
    JComponent wrapped = (JComponent)ContainerUtil.find(component.getComponents(), co -> co.isVisible());
    return wrapped != null ? getLeftInset(wrapped) : 0;
  }

  private static final class ToggleFragmentAction extends ToggleAction implements DumbAware {
    private final SettingsEditorFragment<?, ?> myFragment;

    private ToggleFragmentAction(SettingsEditorFragment<?, ?> fragment) {
      super(fragment.getName());
      myFragment = fragment;
      getTemplatePresentation().setDescription(fragment.getActionHint());
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFragment.isSelected();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFragment.toggle(state);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myFragment.isRemovable());
    }
  }
}
