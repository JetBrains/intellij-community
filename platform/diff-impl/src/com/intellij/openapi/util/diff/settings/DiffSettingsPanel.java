package com.intellij.openapi.util.diff.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.tools.util.base.HighlightPolicy;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DiffSettingsPanel {
  private JPanel myPane;
  private JComponent myComparisonPolicyComponent;
  private JComponent myHighlightPolicyComponent;

  @NotNull private TextDiffSettings myTextSettings;
  @NotNull private TextDiffSettings myDefaultTextSettings;

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  public boolean isModified() {
    if (!myDefaultTextSettings.getComparisonPolicy().equals(myTextSettings.getComparisonPolicy())) return true;
    if (!myDefaultTextSettings.getHighlightPolicy().equals(myTextSettings.getHighlightPolicy())) return true;

    return false;
  }

  public void apply() {
    myDefaultTextSettings.setComparisonPolicy(myTextSettings.getComparisonPolicy());
    myDefaultTextSettings.setHighlightPolicy(myTextSettings.getHighlightPolicy());
  }

  public void reset() {
    myTextSettings.setComparisonPolicy(myDefaultTextSettings.getComparisonPolicy());
    myTextSettings.setHighlightPolicy(myDefaultTextSettings.getHighlightPolicy());
  }

  private void createUIComponents() {
    myTextSettings = TextDiffSettings.getSettings();
    myDefaultTextSettings = TextDiffSettings.getSettingsDefaults();

    MyComparisonPolicySettingAction comparisonPolicyAction = new MyComparisonPolicySettingAction();
    MyHighlightPolicySettingAction highlightPolicyAction = new MyHighlightPolicySettingAction();

    myComparisonPolicyComponent = comparisonPolicyAction.createCustomComponent(comparisonPolicyAction.getTemplatePresentation());
    myHighlightPolicyComponent = highlightPolicyAction.createCustomComponent(highlightPolicyAction.getTemplatePresentation());

    comparisonPolicyAction.update(new AnActionEvent(null, DataManager.getInstance().getDataContext(myComparisonPolicyComponent),
                                                    ActionPlaces.UNKNOWN, comparisonPolicyAction.getTemplatePresentation(),
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

  private class MyComparisonPolicySettingAction extends ComboBoxAction implements DumbAware {
    public MyComparisonPolicySettingAction() {
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
      presentation.setText(myTextSettings.getComparisonPolicy().getText());
      presentation.setEnabled(true);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();

      for (ComparisonPolicy policy : ComparisonPolicy.values()) {
        group.add(new MyPolicyAction(policy));
      }

      return group;
    }

    private class MyPolicyAction extends AnAction implements DumbAware {
      @NotNull private final ComparisonPolicy myPolicy;

      public MyPolicyAction(@NotNull ComparisonPolicy policy) {
        super(policy.getText());
        setEnabledInModalContext(true);
        myPolicy = policy;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (myTextSettings.getComparisonPolicy() == myPolicy) return;
        myTextSettings.setComparisonPolicy(myPolicy);
        MyComparisonPolicySettingAction.this.update(e);
      }
    }
  }
}
