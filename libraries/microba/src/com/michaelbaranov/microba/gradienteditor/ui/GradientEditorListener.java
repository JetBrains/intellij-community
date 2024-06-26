package com.michaelbaranov.microba.gradienteditor.ui;

import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradienteditor.GradientEditor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class GradientEditorListener implements PropertyChangeListener {

  private GradientEditorUI ui;

  private GradientEditor editor;

  public GradientEditorListener(GradientEditor editor, GradientEditorUI ui) {
    super();
    this.editor = editor;
    this.ui = ui;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (GradientBar.PROPERTY_DATA_MODEL.equals(evt.getPropertyName())) {

    }

  }

}
