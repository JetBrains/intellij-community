package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.integration.LocalHistoryBundle;
import com.intellij.localvcs.integration.LocalHistoryConfiguration;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

public class LocalHistoryConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private static final int MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

  private JCheckBox myCbEnabled;
  private JTextField myFieldHistoryLength;
  private JCheckBox myCbProjectOpen;
  private JCheckBox myCbProjectCompile;
  private JCheckBox myCbFileCompile;
  private JCheckBox myCbProjectMake;
  private JCheckBox myCbRunning;
  private JCheckBox myCbUnitTestsPassed;
  private JCheckBox myCbUnitTestsFailed;
  private JPanel myPanel;

  public String getDisplayName() {
    return LocalHistoryBundle.message("lvcs.configurable.display.name");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableLocalVCS.png");
  }

  public String getHelpTopic() {
    return "project.propLocalVCS";
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);

    myCbEnabled = createCheckBox();
    myCbEnabled.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.enable.local.history"));
    myCbEnabled.setAlignmentX(0);
    myCbEnabled.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateEnabled();
      }
    });
    myPanel.add(myCbEnabled, gc);

    gc.insets.top = 10;
    createHistoryPanel(gc);

    createLabelsPanel(gc);

    gc.weighty = 1.0;
    myPanel.add(Box.createVerticalBox(), gc);

    gc.gridx = 1;
    gc.gridheight = 3;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.BOTH;
    myPanel.add(Box.createHorizontalBox(), gc);

    return myPanel;
  }

  private void createHistoryPanel(final GridBagConstraints wholePanelGC) {
    JPanel historyPanel = createPanel(LocalHistoryBundle.message("border.lvcs.properties.history"));

    myFieldHistoryLength = new JTextField();

    JLabel l = new JLabel(LocalHistoryBundle.message("label.lvcs.properties.keep.local.history.count"));
    l.setLabelFor(myFieldHistoryLength);
    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                   GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0);
    historyPanel.add(l, gc);

    Dimension size = new Dimension(30, myFieldHistoryLength.getPreferredSize().height);
    myFieldHistoryLength.setMinimumSize(size);
    myFieldHistoryLength.setPreferredSize(size);
    myFieldHistoryLength.setMaximumSize(size);
    myFieldHistoryLength.setDocument(new MyDocument());
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    historyPanel.add(myFieldHistoryLength, gc);
    myPanel.add(historyPanel, wholePanelGC);
  }

  private void createLabelsPanel(GridBagConstraints wholePanelGC) {
    JPanel labelsPanel = createPanel(LocalHistoryBundle.message("border.lvcs.properties.automatic.labeling.group"));

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);

    myCbProjectOpen = createCheckBox();
    myCbProjectOpen.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.project.opening"));
    labelsPanel.add(myCbProjectOpen, gc);

    myCbProjectCompile = createCheckBox();
    myCbProjectCompile.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.project.compilation"));
    labelsPanel.add(myCbProjectCompile, gc);

    myCbFileCompile = createCheckBox();
    myCbFileCompile.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.file.package.compilation"));
    labelsPanel.add(myCbFileCompile, gc);

    myCbProjectMake = createCheckBox();
    myCbProjectMake.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.project.make"));
    labelsPanel.add(myCbProjectMake, gc);

    myCbRunning = createCheckBox();
    myCbRunning.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.running.debugging"));
    labelsPanel.add(myCbRunning, gc);

    myCbUnitTestsPassed = createCheckBox();
    myCbUnitTestsPassed.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.unit.tests.passed"));
    labelsPanel.add(myCbUnitTestsPassed, gc);

    myCbUnitTestsFailed = createCheckBox();
    myCbUnitTestsFailed.setText(LocalHistoryBundle.message("checkbox.lvcs.properties.unit.tests.failed"));
    labelsPanel.add(myCbUnitTestsFailed, gc);

    labelsPanel.add(Box.createVerticalGlue(), gc);

    myPanel.add(labelsPanel, wholePanelGC);
  }

  private JPanel createPanel(String panelTitle) {
    JPanel labelsPanel = new JPanel(new GridBagLayout());
    labelsPanel.setBorder(
      BorderFactory.createCompoundBorder(IdeBorderFactory.createTitledBorder(panelTitle), BorderFactory.createEmptyBorder(2, 2, 2, 2)));
    return labelsPanel;
  }

  public void apply() throws ConfigurationException {
    LocalHistoryConfiguration c = LocalHistoryConfiguration.getInstance();

    c.LOCAL_HISTORY_ENABLED = myCbEnabled.isSelected();

    c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = myCbFileCompile.isSelected();
    c.ADD_LABEL_ON_PROJECT_COMPILATION = myCbProjectCompile.isSelected();
    c.ADD_LABEL_ON_PROJECT_MAKE = myCbProjectMake.isSelected();
    c.ADD_LABEL_ON_PROJECT_OPEN = myCbProjectOpen.isSelected();
    c.ADD_LABEL_ON_RUNNING = myCbRunning.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_PASSED = myCbUnitTestsPassed.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_FAILED = myCbUnitTestsFailed.isSelected();

    c.PURGING_PERIOD = Long.parseLong(myFieldHistoryLength.getText()) * MILLIS_IN_DAY;

    setModified(false);

  }

  public void reset() {

    LocalHistoryConfiguration c = LocalHistoryConfiguration.getInstance();
    myCbEnabled.setSelected(c.LOCAL_HISTORY_ENABLED);

    myCbFileCompile.setSelected(c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION);
    myCbProjectCompile.setSelected(c.ADD_LABEL_ON_PROJECT_COMPILATION);
    myCbProjectMake.setSelected(c.ADD_LABEL_ON_PROJECT_MAKE);
    myCbProjectOpen.setSelected(c.ADD_LABEL_ON_PROJECT_OPEN);
    myCbRunning.setSelected(c.ADD_LABEL_ON_RUNNING);
    myCbUnitTestsPassed.setSelected(c.ADD_LABEL_ON_UNIT_TEST_PASSED);
    myCbUnitTestsFailed.setSelected(c.ADD_LABEL_ON_UNIT_TEST_FAILED);

    myFieldHistoryLength.setText(String.valueOf(c.PURGING_PERIOD / MILLIS_IN_DAY));

    updateEnabled();

    setModified(false);

  }

  public void disposeUIResources() {
    myPanel = null;
  }

  private void updateEnabled() {
    boolean value = myCbEnabled.isSelected();

    myCbFileCompile.setEnabled(value);
    myCbProjectCompile.setEnabled(value);
    myCbProjectMake.setEnabled(value);
    myCbProjectOpen.setEnabled(value);
    myCbRunning.setEnabled(value);
    myCbUnitTestsPassed.setEnabled(value);
    myCbUnitTestsFailed.setEnabled(value);
    myFieldHistoryLength.setEditable(value);
  }

  private JCheckBox createCheckBox() {
    final JCheckBox box = new JCheckBox();
    box.addChangeListener(new ChangeListener() {
      private boolean myOldValue = box.isSelected();

      public void stateChanged(ChangeEvent e) {
        if (myOldValue != box.isSelected()) {
          setModified(true);
          myOldValue = box.isSelected();
          updateEnabled();
        }
      }
    });

    return box;
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  private class MyDocument extends PlainDocument {
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      char[] source = str.toCharArray();
      char[] result = new char[source.length];
      int j = 0;

      for (int i = 0; i < result.length; i++) {
        if (Character.isDigit(source[i])) {
          result[j++] = source[i];
        }
        else {
          Toolkit.getDefaultToolkit().beep();
        }
      }
      super.insertString(offs, new String(result, 0, j), a);
      setModified(true);
    }
  }
}
