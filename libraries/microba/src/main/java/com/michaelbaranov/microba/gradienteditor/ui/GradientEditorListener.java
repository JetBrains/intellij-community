package com.michaelbaranov.microba.gradienteditor.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import com.michaelbaranov.microba.gradienteditor.GradientEditor;

public class GradientEditorListener implements PropertyChangeListener {

	private GradientEditorUI ui;

	private GradientEditor editor;

	public GradientEditorListener(GradientEditor editor, GradientEditorUI ui) {
		super();
		this.editor = editor;
		this.ui = ui;
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if (GradientEditor.PROPERTY_DATA_MODEL.equals(evt.getPropertyName())) {

		}

	}

}
