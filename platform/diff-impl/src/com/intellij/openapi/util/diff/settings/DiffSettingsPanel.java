package com.intellij.openapi.util.diff.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.diff.tools.util.base.HighlightPolicy;
import com.intellij.openapi.util.diff.tools.util.base.IgnorePolicy;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DiffSettingsPanel {
  private JPanel myPane;
  private JComponent myIgnorePolicyComponent;
  private JComponent myHighlightPolicyComponent;

  @NotNull private TextDiffSettings myTextSettings;
  @NotNull private TextDiffSettings myDefaultTextSettings;

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  public boolean isModified() {
    if (!myDefaultTextSettings.getIgnorePolicy().equals(myTextSettings.getIgnorePolicy())) return true;
    if (!myDefaultTextSettings.getHighlightPolicy().equals(myTextSettings.getHighlightPolicy())) return true;

    return false;
  }

  public void apply() {
    myDefaultTextSettings.setIgnorePolicy(myTextSettings.getIgnorePolicy());
    myDefaultTextSettings.setHighlightPolicy(myTextSettings.getHighlightPolicy());
  }

  public void reset() {
    myTextSettings.setIgnorePolicy(myDefaultTextSettings.getIgnorePolicy());
    myTextSettings.setHighlightPolicy(myDefaultTextSettings.getHighlightPolicy());
  }

  private void createUIComponents() {
    myTextSettings = TextDiffSettings.getSettings();
    myDefaultTextSettings = TextDiffSettings.getSettingsDefaults();

    MyIgnorePolicySettingAction ignorePolicyAction = new MyIgnorePolicySettingAction();
    MyHighlightPolicySettingAction highlightPolicyAction = new MyHighlightPolicySettingAction();

    myIgnorePolicyComponent = ignorePolicyAction.createCustomComponent(ignorePolicyAction.getTemplatePresentation());
    myHighlightPolicyComponent = highlightPolicyAction.createCustomComponent(highlightPolicyAction.getTemplatePresentation());

    ignorePolicyAction.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(myIgnorePolicyComponent),
                                                    ActionPlaces.UNKNOWN, ignorePolicyAction.getTemplatePresentation(),
                                                    ActionManager.getInstance(), 0));
    highlightPolicyAction.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(myHighlightPolicyComponent),
                                                   ActionPlaces.UNKNOWN, highlightPolicyAction.getTemplatePresentation(),
                                                   ActionManager.getInstance(), 0));
  }

  private class MyHighlightPolicySettingAction extends ComboBoxAction implements DumbAware {
    public MyHighlightPolicySettingAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = getTemplatePresentation();

      //noinspection ConstantConditions - inside createUIComponents()
      if (myTextSettings == null) {
        presentation.setEnabled(false);
        return;
      }
      presentation.setText(myTextSettings.getHighlightPolicy().getText());
      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();

      for (HighlightPolicy policy : HighlightPolicy.values()) {
        group.add(new MyPolicyAction(policy));
      }

      return group;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final HighlightPolicy myPolicy;

      public MyPolicyAction(@NotNull HighlightPolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myTextSettings.getHighlightPolicy() == myPolicy) return;
        myTextSettings.setHighlightPolicy(myPolicy);
        MyHighlightPolicySettingAction.this.update(e);
      }
    }
  }

  private class MyIgnorePolicySettingAction extends ComboBoxAction implements DumbAware {
    public MyIgnorePolicySettingAction() {
      setEnabledInModalContext(true);
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = getTemplatePresentation();

      //noinspection ConstantConditions - inside createUIComponents()
      if (myTextSettings == null) {
        presentation.setEnabled(false);
        return;
      }
      presentation.setText(myTextSettings.getIgnorePolicy().getText());
      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();

      for (IgnorePolicy policy : IgnorePolicy.values()) {
        group.add(new MyPolicyAction(policy));
      }

      return group;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final IgnorePolicy myPolicy;

      public MyPolicyAction(@NotNull IgnorePolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myTextSettings.getIgnorePolicy() == myPolicy) return;
        myTextSettings.setIgnorePolicy(myPolicy);
        MyIgnorePolicySettingAction.this.update(e);
      }
    }
  }
}
