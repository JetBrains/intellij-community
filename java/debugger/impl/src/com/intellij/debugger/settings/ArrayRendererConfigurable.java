// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class ArrayRendererConfigurable implements UnnamedConfigurable, Configurable.NoScroll {
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

  @Override
  public void reset() {
    myStartIndex.setText(String.valueOf(myRenderer.START_INDEX));
    myEndIndex.setText(String.valueOf(myRenderer.END_INDEX));
    myEntriesLimit.setText(String.valueOf(myRenderer.ENTRIES_LIMIT));
  }

  @Override
  public void apply() throws ConfigurationException {
    applyTo(myRenderer, true);
  }

  private void applyTo(ArrayRenderer renderer, boolean showBigRangeWarning) throws ConfigurationException {
    int newStartIndex = getInt(myStartIndex);
    int newEndIndex = getInt(myEndIndex);
    int newLimit = getInt(myEntriesLimit);

    if (newStartIndex < 0) {
      throw new ConfigurationException(JavaDebuggerBundle.message("error.array.renderer.configurable.start.index.less.than.zero"));
    }

    if (newEndIndex < newStartIndex) {
      throw new ConfigurationException(JavaDebuggerBundle.message("error.array.renderer.configurable.end.index.less.than.start"));
    }

    if (newStartIndex >= 0 && newEndIndex >= 0) {
      if (newStartIndex > newEndIndex) {
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
          JavaDebuggerBundle.message("warning.range.too.big", ApplicationNamesInfo.getInstance().getProductName()),
          JavaDebuggerBundle.message("title.range.too.big"),
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

  @Override
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

    JLabel startIndexLabel = new JLabel(JavaDebuggerBundle.message("label.array.renderer.configurable.start.index"));
    startIndexLabel.setLabelFor(myStartIndex);

    JLabel endIndexLabel = new JLabel(JavaDebuggerBundle.message("label.array.renderer.configurable.end.index"));
    endIndexLabel.setLabelFor(myEndIndex);

    JLabel entriesLimitLabel = new JLabel(JavaDebuggerBundle.message("label.array.renderer.configurable.max.count1"));
    entriesLimitLabel.setLabelFor(myEntriesLimit);

    myPanel.add(startIndexLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsRight(8), 0, 0));
    myPanel.add(myStartIndex, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insetsRight(8), 0, 0));
    myPanel.add(endIndexLabel, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsRight(8), 0, 0));
    myPanel.add(myEndIndex, new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                   JBInsets.emptyInsets(), 0, 0));

    myPanel.add(entriesLimitLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insets(4, 0, 0, 8), 0, 0));
    myPanel.add(myEntriesLimit, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(4, 0, 0, 8), 0, 0));
    myPanel.add(new JLabel(JavaDebuggerBundle.message("label.array.renderer.configurable.max.count2")), new GridBagConstraints(2, GridBagConstraints.RELATIVE, 2, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsTop(4), 0, 0));

    // push other components up
    myPanel.add(new JLabel(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                     JBInsets.emptyInsets(), 0, 0));

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
      @Override
      public void changedUpdate(DocumentEvent e) {
        updateEntriesLimit();
      }
      @Override
      public void insertUpdate (DocumentEvent e) {
        updateEntriesLimit();
      }
      @Override
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
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateEndIndex();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateEndIndex();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateEndIndex();
      }
    });
    return myPanel;
  }

  private static int getInt(JTextField textField) {
    return StringUtil.parseInt(textField.getText().trim(), 0);
  }

  @Override
  public boolean isModified() {
    ArrayRenderer cloneRenderer = myRenderer.clone();
    try {
      applyTo(cloneRenderer, false);
    }
    catch (ConfigurationException e) {
      return true;
    }
    final boolean valuesEqual =
      (myRenderer.END_INDEX == cloneRenderer.END_INDEX) &&
      (myRenderer.START_INDEX == cloneRenderer.START_INDEX) &&
      (myRenderer.ENTRIES_LIMIT == cloneRenderer.ENTRIES_LIMIT);
    return !valuesEqual;
  }
}
