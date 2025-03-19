package com.intellij.database.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Liudmila Kornilova
 **/
public class BytesLimitPerValueForm {
  private JPanel myPanel;
  private IntegerField myField;

  public BytesLimitPerValueForm() {
  }

  public void reset(@NotNull DataGridSettings settings) {
    myField.setValue(settings.getBytesLimitPerValue());
  }

  public boolean isModified(@NotNull DataGridSettings settings) {
    return settings.getBytesLimitPerValue() != myField.getValue();
  }

  public void validateContent() throws ConfigurationException {
    myField.validateContent();
  }

  public void apply(@NotNull DataGridSettings settings) {
    settings.setBytesLimitPerValue(myField.getValue());
  }

  private void createUIComponents() {
    myField = new IntegerField(null, 1, Integer.MAX_VALUE);
  }

  public @NotNull JPanel getPanel() {
    return myPanel;
  }

  public @NotNull IntegerField getField() {
    return myField;
  }
}
