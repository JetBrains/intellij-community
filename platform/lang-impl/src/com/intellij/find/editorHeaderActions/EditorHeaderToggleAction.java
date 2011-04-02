package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.ui.LabeledIcon;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;

public abstract class EditorHeaderToggleAction extends ToggleAction implements CustomComponentAction {
  private PresentationFactory myPresentationFactory = new PresentationFactory() {
    @Override
    protected Presentation processPresentation(Presentation presentation) {
      LabeledIcon icon = new LabeledIcon(new EmptyIcon(1, 1), presentation.getText(), null);
      icon.setFont(Utils.smaller(icon.getFont()));
      presentation.setIcon(icon);
      return super.processPresentation(presentation);
    }
  };

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return new ActionButton(this, myPresentationFactory.getPresentation(this), "SearchBar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  private EditorSearchComponent myEditorSearchComponent;

  protected EditorHeaderToggleAction(EditorSearchComponent editorSearchComponent, String text, String mnemonic) {
    super(text);
    myEditorSearchComponent = editorSearchComponent;
  }
}
