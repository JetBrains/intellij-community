package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.localvcs.integration.LocalHistoryBundle;
import com.intellij.localvcs.integration.LocalHistoryConfiguration;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

public class LocalHistoryConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private static final int MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

  private JTextField myPurgePeriodField;
  private JCheckBox myProjectOpenBox;
  private JCheckBox myOnProjectCompileBox;
  private JCheckBox myOnFileCompileBox;
  private JCheckBox myOnProjectMakeBox;
  private JCheckBox myOnRunningBox;
  private JCheckBox myOnUnitTestsPassedBox;
  private JCheckBox myOnUnitTestsFailedBox;
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

  public void disposeUIResources() {
    myPanel = null;
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new BorderLayout(0, 5));
    myPanel.add(createPurgePeriodPanel(), BorderLayout.NORTH);
    myPanel.add(createLabelingPanel(), BorderLayout.CENTER);
    return myPanel;
  }

  private JPanel createPurgePeriodPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myPurgePeriodField = new JTextField();

    JLabel l = new JLabel(LocalHistoryBundle.message("label.lvcs.properties.keep.local.history.count"));
    l.setLabelFor(myPurgePeriodField);

    panel.add(l, BorderLayout.CENTER);
    panel.add(myPurgePeriodField, BorderLayout.EAST);

    Dimension size = new Dimension(50, myPurgePeriodField.getPreferredSize().height);
    myPurgePeriodField.setPreferredSize(size);
    myPurgePeriodField.setDocument(new MyDocument());

    return panel;
  }

  private JPanel createLabelingPanel() {
    JPanel panel = new JPanel(new GridLayout(7, 1));
    Border title = IdeBorderFactory.createTitledBorder(LocalHistoryBundle.message("border.lvcs.properties.automatic.labeling.group"));
    panel.setBorder(createCompoundBorder(title, createEmptyBorder(2, 2, 2, 2)));

    myProjectOpenBox = addCheckBox("checkbox.lvcs.properties.project.opening", panel);
    myOnProjectCompileBox = addCheckBox("checkbox.lvcs.properties.project.compilation", panel);
    myOnFileCompileBox = addCheckBox("checkbox.lvcs.properties.file.package.compilation", panel);
    myOnProjectMakeBox = addCheckBox("checkbox.lvcs.properties.project.make", panel);
    myOnRunningBox = addCheckBox("checkbox.lvcs.properties.running.debugging", panel);
    myOnUnitTestsPassedBox = addCheckBox("checkbox.lvcs.properties.unit.tests.passed", panel);
    myOnUnitTestsFailedBox = addCheckBox("checkbox.lvcs.properties.unit.tests.failed", panel);

    return panel;
  }

  private JCheckBox addCheckBox(String messageKey, JPanel p) {
    final JCheckBox cb = new JCheckBox();

    cb.addChangeListener(new ChangeListener() {
      private boolean myOldValue = cb.isSelected();

      public void stateChanged(ChangeEvent e) {
        if (myOldValue != cb.isSelected()) {
          setModified(true);
          myOldValue = cb.isSelected();
        }
      }
    });

    cb.setText(LocalHistoryBundle.message(messageKey));
    p.add(cb);
    return cb;
  }

  public void apply() throws ConfigurationException {
    LocalHistoryConfiguration c = getConfiguration();

    c.PURGE_PERIOD = Long.parseLong(myPurgePeriodField.getText()) * MILLIS_IN_DAY;

    c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = myOnFileCompileBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_COMPILATION = myOnProjectCompileBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_MAKE = myOnProjectMakeBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_OPEN = myProjectOpenBox.isSelected();
    c.ADD_LABEL_ON_RUNNING = myOnRunningBox.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_PASSED = myOnUnitTestsPassedBox.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_FAILED = myOnUnitTestsFailedBox.isSelected();

    setModified(false);
  }

  public void reset() {
    LocalHistoryConfiguration c = getConfiguration();

    myPurgePeriodField.setText(String.valueOf(c.PURGE_PERIOD / MILLIS_IN_DAY));

    myOnFileCompileBox.setSelected(c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION);
    myOnProjectCompileBox.setSelected(c.ADD_LABEL_ON_PROJECT_COMPILATION);
    myOnProjectMakeBox.setSelected(c.ADD_LABEL_ON_PROJECT_MAKE);
    myProjectOpenBox.setSelected(c.ADD_LABEL_ON_PROJECT_OPEN);
    myOnRunningBox.setSelected(c.ADD_LABEL_ON_RUNNING);
    myOnUnitTestsPassedBox.setSelected(c.ADD_LABEL_ON_UNIT_TEST_PASSED);
    myOnUnitTestsFailedBox.setSelected(c.ADD_LABEL_ON_UNIT_TEST_FAILED);

    setModified(false);
  }

  private LocalHistoryConfiguration getConfiguration() {
    return LocalHistory.getConfiguration();
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
