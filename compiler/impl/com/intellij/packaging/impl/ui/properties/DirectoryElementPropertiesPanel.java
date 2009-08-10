package com.intellij.packaging.impl.ui.properties;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class DirectoryElementPropertiesPanel extends ElementWithClasspathPropertiesPanel<DirectoryPackagingElement> {
  private JPanel myMainPanel;
  private JLabel myElementNameLabel;
  private TextFieldWithBrowseButton myClasspathField;

  public DirectoryElementPropertiesPanel(ArtifactEditorContext context) {
    super(context);
    initClasspathField();
  }

  @Override
  protected TextFieldWithBrowseButton getClasspathField() {
    return myClasspathField;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isAvailable(@NotNull DirectoryPackagingElement element) {
    final String name = element.getDirectoryName();
    return name.length() >= 4 && name.charAt(name.length() - 4) == '.' && StringUtil.endsWithIgnoreCase(name, "ar");
  }

  @Override
  public void loadFrom(@NotNull DirectoryPackagingElement element) {
    myElementNameLabel.setText("'" + element.getDirectoryName() + "' manifest properties:");
    super.loadFrom(element);
  }
}
