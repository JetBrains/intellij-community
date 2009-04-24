package com.intellij.packaging.impl.elements;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class ArchiveElementPropertiesPanel extends PackagingElementPropertiesPanel<ArchivePackagingElement> {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private TextFieldWithBrowseButton myClasspathField;

  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  public void loadFrom(@NotNull ArchivePackagingElement element) {
    myMainClassField.setText(element.getMainClass());
    myClasspathField.setText(element.getClasspath());
  }

  public void saveTo(@NotNull ArchivePackagingElement element) {
    element.setMainClass(myMainClassField.getText());
    element.setClasspath(myClasspathField.getText());
  }
}
