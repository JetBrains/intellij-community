/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 4, 2007
 */
public class ObjectMarkupPropertiesDialog extends DialogWrapper {
  private final JTextField myTextMarkupField;
  private final JCheckBox myCbMarkAdditionalFields;
  private final SimpleColoredComponent myColorSample;
  private SimpleTextAttributes myAttributes;
  private final Alarm myUpdateAlarm;
  private static final int UPDATE_DELAY = 200;
  private static Boolean ourMarkCbSavedState;
  private final boolean mySuggestAdditionalMarkup;

  public ObjectMarkupPropertiesDialog(@NotNull final ValueMarkup suggestion, boolean suggestAdditionalMarkup) {
    super(true);
    mySuggestAdditionalMarkup = suggestAdditionalMarkup;
    setTitle("Select object label");
    setModal(true);
    myTextMarkupField = new JTextField(30);
    myCbMarkAdditionalFields = new JCheckBox("Mark values referenced from constant fields", ourMarkCbSavedState == null? suggestAdditionalMarkup : ourMarkCbSavedState);
    myCbMarkAdditionalFields.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        ourMarkCbSavedState = myCbMarkAdditionalFields.isSelected();
      }
    });
    myColorSample = new SimpleColoredComponent();
    myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myAttributes = createAttributes(suggestion.getColor());
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTextMarkupField.setText(suggestion.getText().trim());
        updateLabelSample(0);
      }
    });
    init();
  }


  public JComponent getPreferredFocusedComponent() {
    return myTextMarkupField;
  }

  protected void dispose() {
    myUpdateAlarm.dispose();
    super.dispose();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.add(new JLabel("Label:"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    mainPanel.add(myTextMarkupField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    final JPanel samplePanel = new JPanel(new BorderLayout());
    samplePanel.add(myColorSample, BorderLayout.CENTER);
    samplePanel.setBorder(BorderFactory.createEtchedBorder());
    final FixedSizeButton chooseColorButton = new FixedSizeButton(samplePanel);

    double weighty = mySuggestAdditionalMarkup ? 0.0 : 1.0;
    mainPanel.add(new JLabel("Preview: "), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, weighty, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(samplePanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, weighty, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(chooseColorButton, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, weighty, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));

    if (mySuggestAdditionalMarkup) {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(new MultiLineLabel(
        "If the value is referenced by a constant field of an abstract class,\nIDEA could additionally mark all values referenced from this class with the names of referencing fields."
      ), BorderLayout.CENTER);
      panel.add(myCbMarkAdditionalFields, BorderLayout.SOUTH);
      myCbMarkAdditionalFields.setMnemonic('M');

      mainPanel.add(panel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 0, 0, 0), 0, 0));
    }

    myTextMarkupField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateLabelSample(UPDATE_DELAY);
      }
    });
    chooseColorButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Color color = ColorChooser.chooseColor(myColorSample, "Choose label color", null);
        myAttributes = createAttributes(color);
        updateLabelSample(UPDATE_DELAY);
      }
    });
    return mainPanel;
  }

  private void updateLabelSample(final int updateDelay) {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        myColorSample.clear();
        myColorSample.append(myTextMarkupField.getText().trim(), myAttributes);
        myColorSample.repaint();
      }
    }, updateDelay);
  }
  
  public static Pair<ValueMarkup,Boolean> chooseMarkup(ValueMarkup suggestion, boolean suggestAdditionalMarkup) {
    final ObjectMarkupPropertiesDialog dialog = new ObjectMarkupPropertiesDialog(suggestion, suggestAdditionalMarkup);
    dialog.show();
    if (dialog.isOK()) {
      final String text = dialog.myTextMarkupField.getText().trim();
      final Color color = dialog.myAttributes.getFgColor();
      return text.length() > 0? new Pair<ValueMarkup, Boolean>(new ValueMarkup(text, color, suggestion.getToolTipText()), dialog.myCbMarkAdditionalFields.isSelected()) : null;
    }
    return null;
  }

  private static SimpleTextAttributes createAttributes(final Color color) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color);
  }
}
