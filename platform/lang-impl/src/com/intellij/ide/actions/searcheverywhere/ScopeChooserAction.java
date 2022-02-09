// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.OffsetIcon;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class ScopeChooserAction extends ActionGroup implements CustomComponentAction, DumbAware, SearchEverywhereToggleAction {
  static final char CHOOSE = 'O';
  static final char TOGGLE = 'P';
  static final String TOGGLE_ACTION_NAME = "toggleProjectScope";

  protected ScopeChooserAction() {
    setPopup(true);
    getTemplatePresentation().setPerformGroup(true);
  }

  protected abstract void onScopeSelected(@NotNull ScopeDescriptor o);

  @NotNull
  protected abstract ScopeDescriptor getSelectedScope();

  protected abstract void onProjectScopeToggled();

  protected abstract boolean processScopes(@NotNull Processor<? super ScopeDescriptor> processor);

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) { return EMPTY_ARRAY; }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JComponent component = new ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    ComponentUtil.putClientProperty(component, MnemonicHelper.MNEMONIC_CHECKER, keyCode ->
      KeyEvent.getExtendedKeyCodeForChar(TOGGLE) == keyCode ||
      KeyEvent.getExtendedKeyCodeForChar(CHOOSE) == keyCode);

    MnemonicHelper.registerMnemonicAction(component, CHOOSE);
    InputMap map = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    int mask = MnemonicHelper.getFocusAcceleratorKeyMask();
    map.put(KeyStroke.getKeyStroke(TOGGLE, mask, false), TOGGLE_ACTION_NAME);
    component.getActionMap().put(TOGGLE_ACTION_NAME, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // mimic AnAction event invocation to trigger myEverywhereAutoSet=false logic
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        KeyEvent inputEvent = new KeyEvent(
          component, KeyEvent.KEY_PRESSED, e.getWhen(), MnemonicHelper.getFocusAcceleratorKeyMask(),
          KeyEvent.getExtendedKeyCodeForChar(TOGGLE), TOGGLE);
        AnActionEvent event = AnActionEvent.createFromAnAction(
          ScopeChooserAction.this, inputEvent, ActionPlaces.TOOLBAR, dataContext);
        ActionUtil.performDumbAwareWithCallbacks(ScopeChooserAction.this, event, ScopeChooserAction.this::onProjectScopeToggled);
      }
    });
    return component;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ScopeDescriptor selection = getSelectedScope();
    String name = StringUtil.trimMiddle(StringUtil.notNullize(selection.getDisplayName()), 30);
    String text = StringUtil.escapeMnemonics(name).replaceFirst("(?i)([" + TOGGLE + CHOOSE + "])", "_$1");
    e.getPresentation().setText(text);
    e.getPresentation().setIcon(OffsetIcon.getOriginalIcon(selection.getIcon()));
    String shortcutText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(
      CHOOSE, MnemonicHelper.getFocusAcceleratorKeyMask(), true));
    String shortcutText2 = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(
      TOGGLE, MnemonicHelper.getFocusAcceleratorKeyMask(), true));
    e.getPresentation().setDescription(LangBundle.message("action.choose.scope.p.toggle.scope.description", shortcutText, shortcutText2));
    JComponent button = e.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
    if (button != null) {
      button.setBackground(selection.getColor());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JComponent button = e.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
    if (button == null || !button.isValid()) return;
    ListCellRenderer<ScopeDescriptor> renderer = new ListCellRenderer<>() {
      final ListCellRenderer<ScopeDescriptor> delegate = ScopeChooserCombo.createDefaultRenderer();

      @Override
      public Component getListCellRendererComponent(JList<? extends ScopeDescriptor> list,
                                                    ScopeDescriptor value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        // copied from DarculaJBPopupComboPopup.customizeListRendererComponent()
        Component component = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (component instanceof JComponent &&
            !(component instanceof JSeparator || component instanceof TitledSeparator)) {
          ((JComponent)component).setBorder(JBUI.Borders.empty(2, 8));
        }
        return component;
      }
    };
    List<ScopeDescriptor> items = new ArrayList<>();
    JList<ScopeDescriptor> fakeList = new JBList<>();
    processScopes(o -> {
      Component c = renderer.getListCellRendererComponent(fakeList, o, -1, false, false);
      if (c instanceof JSeparator || c instanceof TitledSeparator ||
          !o.scopeEquals(null) && o.getScope() instanceof GlobalSearchScope) {
        items.add(o);
      }
      return true;
    });
    BaseListPopupStep<ScopeDescriptor> step = new BaseListPopupStep<>("", items) {
      @Nullable
      @Override
      public PopupStep<?> onChosen(ScopeDescriptor selectedValue, boolean finalChoice) {
        onScopeSelected(selectedValue);
        ActionToolbar toolbar = ActionToolbar.findToolbarBy(button);
        if (toolbar != null) toolbar.updateActionsImmediately();
        return FINAL_CHOICE;
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @NotNull
      @Override
      public String getTextFor(ScopeDescriptor value) {
        return value.getScope() instanceof GlobalSearchScope ? StringUtil.notNullize(value.getDisplayName()) : "";
      }

      @Override
      public boolean isSelectable(ScopeDescriptor value) {
        return value.getScope() instanceof GlobalSearchScope;
      }
    };
    ScopeDescriptor selection = getSelectedScope();
    step.setDefaultOptionIndex(ContainerUtil.indexOf(items, o -> Objects.equals(o.getDisplayName(), selection.getDisplayName())));
    ListPopupImpl popup = new ListPopupImpl(e.getProject(), step);
    popup.setMaxRowCount(10);
    //noinspection unchecked
    popup.getList().setCellRenderer(renderer);
    popup.showUnderneathOf(button);
  }
}