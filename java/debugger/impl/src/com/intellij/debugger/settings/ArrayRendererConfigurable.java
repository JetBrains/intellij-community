/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class ArrayRendererConfigurable implements UnnamedConfigurable{
  private JTextField myEntriesLimit;
  private JTextField myStartIndex;
  private JTextField myEndIndex;
  private boolean myEntriesLimitUpdateEnabled = true;
  private boolean myIndexUpdateEnabled = true;
  
  private final ArrayRenderer myRenderer;
  private JComponent myPanel;

  public ArrayRendererConfigurable(ArrayRenderer renderer) {
    myRenderer = renderer;
  }

  public ArrayRenderer getRenderer() {
    return myRenderer;
  }

  public void reset() {
    myStartIndex.setText(String.valueOf(myRenderer.START_INDEX));
    myEndIndex.setText(String.valueOf(myRenderer.END_INDEX));
    myEntriesLimit.setText(String.valueOf(myRenderer.ENTRIES_LIMIT));
  }

  public void apply() {
    applyTo(myRenderer, true);
  }

  private void applyTo(ArrayRenderer renderer, boolean showBigRangeWarning) {
    int newStartIndex = getInt(myStartIndex);
    int newEndIndex = getInt(myEndIndex);
    int newLimit = getInt(myEntriesLimit);

    if (newStartIndex >= 0 && newEndIndex >= 0) {
      if (newStartIndex >= newEndIndex) {
        int currentStartIndex = renderer.START_INDEX;
        int currentEndIndex = renderer.END_INDEX;
        newEndIndex = newStartIndex + (currentEndIndex - currentStartIndex);
      }

      if(newLimit <= 0) {
        newLimit = 1;
      }

      if(showBigRangeWarning && (newEndIndex - newStartIndex > 10000)) {
        final int answer = Messages.showOkCancelDialog(
          myPanel.getRootPane(),
          DebuggerBundle.message("warning.range.too.big", ApplicationNamesInfo.getInstance().getProductName()),
          DebuggerBundle.message("title.range.too.big"),
          Messages.getWarningIcon());
        if(answer != Messages.OK) {
          return;
        }
      }
    }

    renderer.START_INDEX   = newStartIndex;
    renderer.END_INDEX     = newEndIndex;
    renderer.ENTRIES_LIMIT = newLimit;
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout());

    myStartIndex = new JTextField(5);
    myEndIndex = new JTextField(5);
    myEntriesLimit = new JTextField(5);

    final FontMetrics fontMetrics = myStartIndex.getFontMetrics(myStartIndex.getFont());
    final Dimension minSize = new Dimension(myStartIndex.getPreferredSize());
    //noinspection HardCodedStringLiteral
    minSize.width = fontMetrics.stringWidth("AAAAA");
    myStartIndex.setMinimumSize(minSize);
    myEndIndex.setMinimumSize(minSize);
    myEntriesLimit.setMinimumSize(minSize);

    JLabel startIndexLabel = new JLabel(DebuggerBundle.message("label.array.renderer.configurable.start.index"));
    startIndexLabel.setLabelFor(myStartIndex);

    JLabel endIndexLabel = new JLabel(DebuggerBundle.message("label.array.renderer.configurable.end.index"));
    endIndexLabel.setLabelFor(myEndIndex);

    JLabel entriesLimitLabel = new JLabel(DebuggerBundle.message("label.array.renderer.configurable.max.count1"));
    entriesLimitLabel.setLabelFor(myEntriesLimit);

    myPanel.add(startIndexLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    myPanel.add(myStartIndex, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    myPanel.add(endIndexLabel, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    myPanel.add(myEndIndex, new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    myPanel.add(entriesLimitLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 8), 0, 0));
    myPanel.add(myEntriesLimit, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 8), 0, 0));
    myPanel.add(new JLabel(DebuggerBundle.message("label.array.renderer.configurable.max.count2")), new GridBagConstraints(2, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));

    final DocumentListener listener = new DocumentListener() {
      private void updateEntriesLimit() {
        final boolean state = myIndexUpdateEnabled;
        myIndexUpdateEnabled = false;
        try {
          if (myEntriesLimitUpdateEnabled) {
            myEntriesLimit.setText(String.valueOf(getInt(myEndIndex) - getInt(myStartIndex) + 1));
          }
        }
        finally {
          myIndexUpdateEnabled = state;
        }
      }
      public void changedUpdate(DocumentEvent e) {
        updateEntriesLimit();
      }
      public void insertUpdate (DocumentEvent e) {
        updateEntriesLimit();
      }
      public void removeUpdate (DocumentEvent e) {
        updateEntriesLimit();
      }
    };
    myStartIndex.getDocument().addDocumentListener(listener);
    myEndIndex.getDocument().addDocumentListener(listener);
    myEntriesLimit.getDocument().addDocumentListener(new DocumentListener() {
      private void updateEndIndex() {
        final boolean state = myEntriesLimitUpdateEnabled;
        myEntriesLimitUpdateEnabled = false;
        try {
          if (myIndexUpdateEnabled) {
            myEndIndex.setText(String.valueOf(getInt(myEntriesLimit) + getInt(myStartIndex) - 1));
          }
        }
        finally {
          myEntriesLimitUpdateEnabled = state;
        }
      }
      public void insertUpdate(DocumentEvent e) {
        updateEndIndex();
      }

      public void removeUpdate(DocumentEvent e) {
        updateEndIndex();
      }

      public void changedUpdate(DocumentEvent e) {
        updateEndIndex();
      }
    });
    return myPanel;
  }

  private static int getInt(JTextField textField) {
    int newEndIndex = 0;
    try {
      newEndIndex = Integer.parseInt(textField.getText().trim());
    }
    catch (NumberFormatException exception) {
      // ignored
    }
    return newEndIndex;
  }

  public boolean isModified() {
    ArrayRenderer cloneRenderer = myRenderer.clone();
    applyTo(cloneRenderer, false);
    final boolean valuesEqual =
      (myRenderer.END_INDEX == cloneRenderer.END_INDEX) &&
      (myRenderer.START_INDEX == cloneRenderer.START_INDEX) &&
      (myRenderer.ENTRIES_LIMIT == cloneRenderer.ENTRIES_LIMIT);
    return !valuesEqual;
  }

  public void disposeUIResources() {
  }
}
