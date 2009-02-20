/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 4, 2007
 */
public class ObjectMarkupPropertiesDialog extends DialogWrapper {
  private final JTextField myTextMarkupField;
  private final SimpleColoredComponent myColorSample;
  private SimpleTextAttributes myAttributes;
  private final Alarm myUpdateAlarm;
  private static final int UPDATE_DELAY = 200;

  public ObjectMarkupPropertiesDialog(final String suggestedName) {
    super(true);
    setTitle("Select object label");
    setModal(true);
    myTextMarkupField = new JTextField(30);
    myColorSample = new SimpleColoredComponent();
    myAttributes = createAttributes(Color.RED);
    myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    if (suggestedName != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myTextMarkupField.setText(suggestedName.trim());
          updateLabelSample(0);
        }
      });
    }
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
    mainPanel.add(new JLabel("Label:"), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    mainPanel.add(myTextMarkupField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));


    final JPanel samplePanel = new JPanel(new BorderLayout());
    samplePanel.add(myColorSample, BorderLayout.CENTER);
    samplePanel.setBorder(BorderFactory.createEtchedBorder());
    final FixedSizeButton chooseColorButton = new FixedSizeButton(samplePanel);

    mainPanel.add(new JLabel("Preview: "), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(samplePanel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 0, 0), 0, 0));
    mainPanel.add(chooseColorButton, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));

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
  
  public static ValueMarkup chooseMarkup(final String suggestedName) {
    final ObjectMarkupPropertiesDialog dialog = new ObjectMarkupPropertiesDialog(suggestedName);
    dialog.show();
    if (dialog.isOK()) {
      final String text = dialog.myTextMarkupField.getText().trim();
      final Color color = dialog.myAttributes.getFgColor();
      return text.length() > 0? new ValueMarkup(text, color) : null;  
    }
    return null;
  }

  private static SimpleTextAttributes createAttributes(final Color color) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color);
  }
}
