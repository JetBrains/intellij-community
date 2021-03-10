// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.CompositeSettingsBuilder;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.IdeFocusManager;
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

  private final Disposable myDisposable;
  private final JPanel myPanel = new JPanel(new GridBagLayout()) {
    @Override
    public void addNotify() {
      super.addNotify();
      registerShortcuts();
    }
  };
  private final GridBagConstraints myConstraints =
    new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(TOP_INSET), 0, 0);
  private final Collection<? extends SettingsEditorFragment<Settings, ?>> myFragments;
  private final SettingsEditorFragment<Settings, ?> myMain;
  private DropDownLink<String> myLinkLabel;

  FragmentedSettingsBuilder(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments,
                            SettingsEditorFragment<Settings, ?> main, @NotNull Disposable disposable) {
    myFragments = fragments;
    myMain = main;
    myDisposable = disposable;
  }

  @Override
  public @NotNull Collection<SettingsEditor<Settings>> getEditors() {
    return new ArrayList<>(myFragments);
  }

  @Override
  public @NotNull JComponent createCompoundEditor() {
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

  private void buildBeforeRun(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments) {
    SettingsEditorFragment<Settings, ?> beforeRun = ContainerUtil.find(fragments, fragment -> fragment.getCommandLinePosition() == -2);
    if (beforeRun != null) {
      addLine(beforeRun.getComponent(), TOP_INSET, 0, TOP_INSET * 2);
      fragments.remove(beforeRun);
    }
  }

  private JComponent buildHeader(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments) {
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
    CustomShortcutSet shortcutSet = KeymapUtil.getMnemonicAsShortcut(TextWithMnemonic.parse(message).getMnemonic());
    if (shortcutSet != null) {
      List<String> list = ContainerUtil.map(shortcutSet.getShortcuts(), shortcut -> KeymapUtil.getShortcutText(shortcut));
      list.sort(Comparator.comparingInt(String::length));
      JLabel shortcutLabel = new JLabel(list.get(0));
      shortcutLabel.setEnabled(false);
      shortcutLabel.setBorder(JBUI.Borders.empty(0, 5));
      linkPanel.add(shortcutLabel, BorderLayout.EAST);
    }
    panel.add(linkPanel, BorderLayout.EAST);
    return panel;
  }

  private void registerShortcuts() {
    for (AnAction action : buildGroup(new Ref<>()).getChildActionsOrStubs()) {
      ShortcutSet shortcutSet = action.getShortcutSet();
      if (shortcutSet.getShortcuts().length > 0 && action instanceof ToggleFragmentAction) {
        new AnAction(action.getTemplateText()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            SettingsEditorFragment<?, ?> fragment = ((ToggleFragmentAction)action).myFragment;
            fragment.toggle(true, e); // show or set focus
            IdeFocusManager.getGlobalInstance().requestFocus(fragment.getEditorComponent(), false);
          }
        }.registerCustomShortcutSet(shortcutSet, myPanel.getRootPane(), myDisposable);
      }
    }
  }

  private JBPopup showOptions() {
    DataContext dataContext = DataManager.getInstance().getDataContext(myLinkLabel);
    Ref<JComponent> lastSelected = new Ref<>();
    DefaultActionGroup group = buildGroup(lastSelected);
    Runnable callback = () -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        JComponent component = lastSelected.get();
        if (component != null && !(component instanceof JPanel) && !(component instanceof JLabel)) {
          IdeFocusManager.getGlobalInstance().requestFocus(component, false);
        }
      });
    };
    String title = myMain != null ? IdeBundle.message("popup.title.add.group.options", myMain.getGroup()) :
                   IdeBundle.message("popup.title.add.run.options");
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(title,
                                                                          group,
                                                                          dataContext,
                                                                          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                                                          callback, -1);
    popup.setHandleAutoSelectionBeforeShow(true);
    popup.addListSelectionListener(e -> {
      JBPopup jbPopup = PopupUtil.getPopupContainerFor((Component)e.getSource());
      AnActionHolder data = (AnActionHolder)PlatformDataKeys.SELECTED_ITEM.getData((DataProvider)e.getSource());
      jbPopup.setAdText(getHint(data == null ? null : data.getAction()), SwingConstants.LEFT);
    });
    return popup;
  }

  @NotNull
  private static @NlsContexts.PopupAdvertisement String getHint(AnAction action) {
    return (action != null && StringUtil.isNotEmpty(action.getTemplatePresentation().getDescription())) ?
           action.getTemplatePresentation().getDescription() : "";
  }

  @NotNull
  private DefaultActionGroup buildGroup(List<? extends SettingsEditorFragment<Settings, ?>> fragments,
                                        Ref<? super JComponent> lastSelected) {
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
      ToggleFragmentAction action = new ToggleFragmentAction(fragment, lastSelected);
      ShortcutSet shortcutSet = ActionUtil.getMnemonicAsShortcut(action);
      if (shortcutSet != null) {
        action.registerCustomShortcutSet(shortcutSet, null);
      }
      actionGroup.add(action);
      List<SettingsEditorFragment<Settings, ?>> children = fragment.getChildren();
      if (!children.isEmpty()) {
        DefaultActionGroup childGroup = buildGroup(children, lastSelected);
        childGroup.setPopup(true);
        childGroup.getTemplatePresentation().setText(fragment.getChildrenGroupName());
        actionGroup.add(childGroup);
      }
    }
    return actionGroup;
  }

  private DefaultActionGroup buildGroup(Ref<? super JComponent> lastSelected) {
    DefaultActionGroup group = buildGroup(ContainerUtil.filter(myFragments, fragment -> fragment.getName() != null), lastSelected);
    if (myMain != null) {
      group.add(Separator.create(), Constraints.FIRST);
      group.add(new ToggleFragmentAction(myMain, lastSelected), Constraints.FIRST);
    }
    return group;
  }

  private List<SettingsEditorFragment<Settings, ?>> restoreGroups(List<? extends SettingsEditorFragment<Settings, ?>> fragments) {
    ArrayList<SettingsEditorFragment<Settings, ?>> result = new ArrayList<>();
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      String group = fragment.getGroup();
      int last = ContainerUtil.lastIndexOf(result, f -> f.getGroup() == group);
      result.add(last >= 0 ? last + 1 : result.size(), fragment);
    }
    return result;
  }

  private void buildCommandLinePanel(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments) {
    List<SettingsEditorFragment<Settings, ?>> list = ContainerUtil.filter(fragments, fragment -> fragment.getCommandLinePosition() > 0);
    if (list.isEmpty()) return;
    fragments.removeAll(list);
    CommandLinePanel panel = new CommandLinePanel(list, myDisposable);
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
    private final Ref<? super JComponent> myLastSelected;

    private ToggleFragmentAction(SettingsEditorFragment<?, ?> fragment, Ref<? super JComponent> lastSelected) {
      super(fragment.getName());
      myFragment = fragment;
      myLastSelected = lastSelected;
      getTemplatePresentation().setDescription(fragment.getActionHint());

      if (fragment.getActionDescription() != null) {
        getTemplatePresentation().putClientProperty(Presentation.PROP_VALUE, fragment.getActionDescription());
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFragment.isSelected();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFragment.toggle(state, e);
      if (state) {
        myLastSelected.set(myFragment.getEditorComponent());
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myFragment.isRemovable());
    }
  }
}
