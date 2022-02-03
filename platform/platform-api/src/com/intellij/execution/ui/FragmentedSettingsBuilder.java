// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.components.DropDownLink;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class FragmentedSettingsBuilder<Settings extends FragmentedSettings> implements CompositeSettingsBuilder<Settings>, Disposable {

  public static final int TOP_INSET = 6;
  public static final int LEFT_INSET = 20;
  public static final int MEDIUM_TOP_INSET = 8;
  public static final int LARGE_TOP_INSET = 20;

  public static final int TAG_VGAP = 6;
  public static final int TAG_HGAP = 2;

  private Disposable myDisposable;
  private final JPanel myPanel = new JPanel(new GridBagLayout()) {
    @Override
    public void addNotify() {
      super.addNotify();
      myDisposable = Disposer.newDisposable();
      registerShortcuts();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      Disposer.dispose(myDisposable);
    }
  };
  private final GridBagConstraints myConstraints =
    new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.insetsTop(TOP_INSET), 0, 0);
  private final Collection<? extends SettingsEditorFragment<Settings, ?>> myFragments;
  private final @Nullable NestedGroupFragment<Settings> myMain;
  protected int myGroupInset;
  private DropDownLink<String> myLinkLabel;
  private String myConfigId; // for FUS

  public FragmentedSettingsBuilder(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments,
                                   @Nullable NestedGroupFragment<Settings> main,
                                   @NotNull Disposable parentDisposable) {
    myFragments = fragments;
    myMain = main;
    Disposer.register(parentDisposable, this);
  }

  @Override
  public void dispose() {
    if (myDisposable != null && !Disposer.isDisposed(myDisposable)) {
      Disposer.dispose(myDisposable);
    }
  }

  @Override
  public @NotNull Collection<SettingsEditor<Settings>> getEditors() {
    return new ArrayList<>(myFragments);
  }

  @Override
  public @NotNull JComponent createCompoundEditor() {
    List<SettingsEditorFragment<Settings, ?>> fragments = new ArrayList<>(myFragments);
    fragments.sort(Comparator.comparingInt(SettingsEditorFragment::getPriority));

    SettingsEditorFragment<Settings, ?> beforeRun = ContainerUtil.find(fragments, fragment -> fragment.isBeforeRun());
    SettingsEditorFragment<Settings, ?> header = ContainerUtil.find(fragments, fragment -> fragment.isHeader());
    List<SettingsEditorFragment<Settings, ?>> commandLine = ContainerUtil.filter(fragments, fragment -> fragment.isCommandLine());
    List<SettingsEditorFragment<Settings, ?>> subGroups = ContainerUtil.filter(fragments, fragment -> !fragment.getChildren().isEmpty());
    List<SettingsEditorFragment<Settings, ?>> tags = ContainerUtil.filter(fragments, it -> it.isTag());

    fragments.remove(beforeRun);
    fragments.remove(header);
    fragments.removeAll(commandLine);
    fragments.removeAll(subGroups);
    fragments.removeAll(tags);

    if (myMain == null) {
      myPanel.setBorder(JBUI.Borders.emptyLeft(5));
      addLine(new JSeparator());
    }

    addBeforeRun(beforeRun);
    addHeader(header);

    myGroupInset = myMain == null ? 0 : LEFT_INSET;
    if (myMain != null && myMain.component() != null) {
      addLine(myMain.component());
    }

    addCommandLinePanel(commandLine);
    addFragments(fragments);
    addTagPanel(tags);
    addSubGroups(subGroups);

    myGroupInset = 0;
    if (myMain == null) {
      myConstraints.weighty = 1;
      myPanel.add(new JPanel(), myConstraints);
    }
    return myPanel;
  }

  protected void addLine(Component component, int top, int left, int bottom) {
    myConstraints.insets = JBUI.insets(top, left + myGroupInset, bottom, 0);
    myPanel.add(component, myConstraints.clone());
    myConstraints.gridy++;
    myConstraints.insets = JBUI.insetsTop(top);
  }

  protected void addLine(Component component) {
    addLine(component, TOP_INSET, 0, 0);
  }

  protected void addBeforeRun(@Nullable SettingsEditorFragment<Settings, ?> beforeRun) {
    if (beforeRun != null) {
      addLine(beforeRun.getComponent(), TOP_INSET, 0, TOP_INSET * 2);
    }
  }

  protected void addHeader(@Nullable SettingsEditorFragment<Settings, ?> header) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(5, 0));
    if (header != null) {
      panel.add(header.getComponent(), BorderLayout.WEST);
    }
    var separator = createHeaderSeparator();
    if (separator != null) {
      panel.add(separator, BorderLayout.CENTER);
    }
    String message = OptionsBundle.message(myMain == null ? "settings.editor.modify.options" : "settings.editor.modify");
    myLinkLabel = new DropDownLink<>(message, link -> showOptions());
    myLinkLabel.setBorder(JBUI.Borders.emptyLeft(5));
    JPanel linkPanel = new JPanel(new BorderLayout());
    linkPanel.add(myLinkLabel, BorderLayout.CENTER);
    CustomShortcutSet shortcutSet = KeymapUtil.getMnemonicAsShortcut(TextWithMnemonic.parse(message).getMnemonicCode());
    if (shortcutSet != null) {
      List<String> list = ContainerUtil.map(shortcutSet.getShortcuts(), shortcut -> KeymapUtil.getShortcutText(shortcut));
      list.sort(Comparator.comparingInt(String::length));
      JLabel shortcutLabel = new JLabel(list.get(0));
      shortcutLabel.setEnabled(false);
      shortcutLabel.setBorder(JBUI.Borders.empty(0, 5));
      linkPanel.add(shortcutLabel, BorderLayout.EAST);
    }
    panel.add(linkPanel, BorderLayout.EAST);
    addLine(panel, myMain == null ? TOP_INSET : LARGE_TOP_INSET, 0, 0);
  }

  protected @Nullable JComponent createHeaderSeparator() {
    return myMain != null ? SeparatorFactory.createSeparator(myMain.getGroup(), null) : null;
  }

  protected void addFragments(@NotNull List<SettingsEditorFragment<Settings, ?>> fragments) {
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      JComponent component = fragment.getComponent();
      addLine(component);
      if (fragment.getHintComponent() != null) {
        addLine(fragment.getHintComponent(), TOP_INSET, getLeftInset(component), 0);
      }
    }
  }

  protected void addSubGroups(@NotNull List<SettingsEditorFragment<Settings, ?>> subGroups) {
    for (SettingsEditorFragment<Settings, ?> group : subGroups) {
      addLine(group.getComponent(), 0, 0, 0);
    }
  }

  protected void addTagPanel(@NotNull List<SettingsEditorFragment<Settings, ?>> tags) {
    JPanel tagsPanel = new JPanel(new WrapLayout(FlowLayout.LEADING, JBUI.scale(TAG_HGAP), JBUI.scale(TAG_VGAP)));
    for (SettingsEditorFragment<Settings, ?> tag : tags) {
      tagsPanel.add(tag.getComponent());
    }
    if (tagsPanel.getComponentCount() > 0) {
      hideWhenChildrenIsInvisible(tagsPanel);
      addLine(tagsPanel, MEDIUM_TOP_INSET, -getLeftInset((JComponent)tagsPanel.getComponent(0)) - TAG_HGAP, 0);
    }
  }

  private static void hideWhenChildrenIsInvisible(JComponent component) {
    Component[] children = component.getComponents();
    component.setVisible(ContainerUtil.exists(children, it -> it.isVisible()));
    for (var child : children) {
      UIUtil.runWhenVisibilityChanged(child, () -> component.setVisible(ContainerUtil.exists(children, it -> it.isVisible())));
    }
  }

  private void registerShortcuts() {
    for (AnAction action : buildGroup(new Ref<>()).getChildActionsOrStubs()) {
      ShortcutSet shortcutSet = action.getShortcutSet();
      if (shortcutSet.getShortcuts().length > 0 && action instanceof ToggleFragmentAction) {
        new DumbAwareAction(action.getTemplateText()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            SettingsEditorFragment<?, ?> fragment = ((ToggleFragmentAction)action).myFragment;
            fragment.toggle(true, e); // show or set focus
            IdeFocusManager.getGlobalInstance().requestFocus(fragment.getEditorComponent(), false);
            FragmentStatisticsService.getInstance().logNavigateOption(e.getProject(), fragment.getId(), myConfigId, e);
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
      Object selectedItem = PlatformCoreDataKeys.SELECTED_ITEM.getData((DataProvider)e.getSource());
      if (selectedItem instanceof AnActionHolder) {
        jbPopup.setAdText(getHint(((AnActionHolder)selectedItem).getAction()), SwingConstants.LEFT);
      }
    });
    return popup;
  }

  public void setConfigId(String configId) {
    myConfigId = configId;
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
      List<SettingsEditorFragment<Settings, ?>> children = fragment.getChildren();
      if (children.isEmpty()) {
        ToggleFragmentAction action = new ToggleFragmentAction(fragment, lastSelected);
        ShortcutSet shortcutSet = ActionUtil.getMnemonicAsShortcut(action);
        if (shortcutSet != null) {
          action.registerCustomShortcutSet(shortcutSet, null);
        }
        actionGroup.add(action);
      }
      else {
        DefaultActionGroup childGroup = buildGroup(children, lastSelected);
        childGroup.getTemplatePresentation().setText(fragment.getChildrenGroupName());
        actionGroup.add(childGroup);
      }
    }
    return actionGroup;
  }

  private DefaultActionGroup buildGroup(Ref<? super JComponent> lastSelected) {
    return buildGroup(ContainerUtil.filter(myFragments, fragment -> fragment.getName() != null), lastSelected);
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

  private void addCommandLinePanel(@NotNull List<SettingsEditorFragment<Settings, ?>> commandLine) {
    if (!commandLine.isEmpty()) {
      CommandLinePanel panel = new CommandLinePanel(commandLine, myConfigId, this);
      addLine(panel, 0, -panel.getLeftInset(), TOP_INSET);
    }
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
