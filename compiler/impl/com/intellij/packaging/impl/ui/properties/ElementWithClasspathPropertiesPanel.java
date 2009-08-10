package com.intellij.packaging.impl.ui.properties;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.impl.elements.CompositeElementWithClasspath;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author nik
 */
public abstract class ElementWithClasspathPropertiesPanel<E extends CompositeElementWithClasspath<?>> extends PackagingElementPropertiesPanel<E> {
  private final ArtifactEditorContext myContext;

  public ElementWithClasspathPropertiesPanel(ArtifactEditorContext context) {
    myContext = context;
  }

  protected void initClasspathField() {
    getClasspathField().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(getClasspathField().getTextField(), "Edit Classpath", "classpath-attribute-editor");
      }
    });
    getClasspathField().getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myContext.queueValidation();
      }
    });
  }

  protected abstract TextFieldWithBrowseButton getClasspathField();

  @Override
  public void loadFrom(@NotNull E element) {
    getClasspathField().setText(StringUtil.join(element.getClasspath(), " "));
  }

  @Override
  public boolean isModified(@NotNull E original) {
    return !original.getClasspath().equals(getConfiguredClasspath());
  }

  @Override
  public void saveTo(@NotNull E element) {
    element.setClasspath(getConfiguredClasspath());
  }

  private List<String> getConfiguredClasspath() {
    return StringUtil.split(getClasspathField().getText(), " ");
  }
}
