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
package com.intellij.diff.settings;

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Dictionary;
import java.util.Hashtable;

public class DiffSettingsPanel {
  private JPanel myPane;
  private ContextRangePanel myContextRangeComponent;
  private JCheckBox myGoToNextFileOnNextDifferenceCheckbox;
  private JCheckBox myAutoApplyNonConflictedChangesCheckbox;
  private JCheckBox myMergeLstGutterMarkers;

  @NotNull private final TextDiffSettings myTextSettings = TextDiffSettings.getSettings();
  @NotNull private final DiffSettings myDiffSettings = DiffSettings.getSettings();

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  public boolean isModified() {
    if (myContextRangeComponent.isModified()) return true;
    if (myGoToNextFileOnNextDifferenceCheckbox.isSelected() != myDiffSettings.isGoToNextFileOnNextDifference()) return true;
    if (myAutoApplyNonConflictedChangesCheckbox.isSelected() != myTextSettings.isAutoApplyNonConflictedChanges()) return true;
    if (myMergeLstGutterMarkers.isSelected() != myTextSettings.isEnableLstGutterMarkersInMerge()) return true;
    return false;
  }

  public void apply() {
    myContextRangeComponent.apply();
    myDiffSettings.setGoToNextFileOnNextDifference(myGoToNextFileOnNextDifferenceCheckbox.isSelected());
    myTextSettings.setAutoApplyNonConflictedChanges(myAutoApplyNonConflictedChangesCheckbox.isSelected());
    myTextSettings.setEnableLstGutterMarkersInMerge(myMergeLstGutterMarkers.isSelected());
  }

  public void reset() {
    myContextRangeComponent.reset();
    myGoToNextFileOnNextDifferenceCheckbox.setSelected(myDiffSettings.isGoToNextFileOnNextDifference());
    myAutoApplyNonConflictedChangesCheckbox.setSelected(myTextSettings.isAutoApplyNonConflictedChanges());
    myMergeLstGutterMarkers.setSelected(myTextSettings.isEnableLstGutterMarkersInMerge());
  }

  private void createUIComponents() {
    myContextRangeComponent = new ContextRangePanel();
  }

  protected class ContextRangePanel extends JSlider {
    public ContextRangePanel() {
      super(SwingConstants.HORIZONTAL, 0, TextDiffSettingsHolder.CONTEXT_RANGE_MODES.length - 1, 0);
      setMinorTickSpacing(1);
      setPaintTicks(true);
      setPaintTrack(true);
      setSnapToTicks(true);
      UIUtil.setSliderIsFilled(this, true);
      setPaintLabels(true);

      //noinspection UseOfObsoleteCollectionType
      Dictionary<Integer, JLabel> sliderLabels = new Hashtable<>();
      for (int i = 0; i < TextDiffSettingsHolder.CONTEXT_RANGE_MODES.length; i++) {
        sliderLabels.put(i, new JLabel(TextDiffSettingsHolder.CONTEXT_RANGE_MODE_LABELS[i]));
      }
      setLabelTable(sliderLabels);
    }

    public void apply() {
      myTextSettings.setContextRange(getContextRange());
    }

    public void reset() {
      setContextRange(myTextSettings.getContextRange());
    }

    public boolean isModified() {
      return getContextRange() != myTextSettings.getContextRange();
    }

    private int getContextRange() {
      return TextDiffSettingsHolder.CONTEXT_RANGE_MODES[getValue()];
    }

    private void setContextRange(int value) {
      for (int i = 0; i < TextDiffSettingsHolder.CONTEXT_RANGE_MODES.length; i++) {
        int mark = TextDiffSettingsHolder.CONTEXT_RANGE_MODES[i];
        if (mark == value) {
          setValue(i);
        }
      }
    }
  }
}
