/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.settings;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.diff.tools.util.base.HighlightPolicy;
import com.intellij.openapi.util.diff.tools.util.base.IgnorePolicy;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Dictionary;
import java.util.Hashtable;

public class DiffSettingsPanel {
  private JPanel myPane;
  private JComponent myIgnorePolicyComponent;
  private JComponent myHighlightPolicyComponent;
  private ContextRangePanel myContextRangeComponent;

  @NotNull private TextDiffSettings myTextSettings;
  @NotNull private TextDiffSettings myDefaultTextSettings;

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  public boolean isModified() {
    if (!myDefaultTextSettings.getIgnorePolicy().equals(myTextSettings.getIgnorePolicy())) return true;
    if (!myDefaultTextSettings.getHighlightPolicy().equals(myTextSettings.getHighlightPolicy())) return true;
    if (myContextRangeComponent.isModified()) return true;
    return false;
  }

  public void apply() {
    myDefaultTextSettings.setIgnorePolicy(myTextSettings.getIgnorePolicy());
    myDefaultTextSettings.setHighlightPolicy(myTextSettings.getHighlightPolicy());
    myContextRangeComponent.apply();
  }

  public void reset() {
    myTextSettings.setIgnorePolicy(myDefaultTextSettings.getIgnorePolicy());
    myTextSettings.setHighlightPolicy(myDefaultTextSettings.getHighlightPolicy());
    myContextRangeComponent.reset();
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

    myContextRangeComponent = new ContextRangePanel();
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

  protected class ContextRangePanel extends JSlider {
    public ContextRangePanel() {
      super(SwingConstants.HORIZONTAL, 0, TextDiffSettings.CONTEXT_RANGE_MODES.length - 1, 0);
      setMinorTickSpacing(1);
      setPaintTicks(true);
      setPaintTrack(true);
      setSnapToTicks(true);
      UIUtil.setSliderIsFilled(this, true);
      setPaintLabels(true);

      //noinspection UseOfObsoleteCollectionType
      Dictionary<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
      for (int i = 0; i < TextDiffSettings.CONTEXT_RANGE_MODES.length; i++) {
        sliderLabels.put(i, new JLabel(TextDiffSettings.CONTEXT_RANGE_MODE_LABELS[i]));
      }
      setLabelTable(sliderLabels);
    }

    public void apply() {
      myDefaultTextSettings.setContextRange(getContextRange());
    }

    public void reset() {
      setContextRange(myDefaultTextSettings.getContextRange());
    }

    public boolean isModified() {
      return getContextRange() != myDefaultTextSettings.getContextRange();
    }

    private int getContextRange() {
      return TextDiffSettings.CONTEXT_RANGE_MODES[getValue()];
    }

    private void setContextRange(int value) {
      for (int i = 0; i < TextDiffSettings.CONTEXT_RANGE_MODES.length; i++) {
        int mark = TextDiffSettings.CONTEXT_RANGE_MODES[i];
        if (mark == value) {
          setValue(i);
        }
      }
    }
  }
}
