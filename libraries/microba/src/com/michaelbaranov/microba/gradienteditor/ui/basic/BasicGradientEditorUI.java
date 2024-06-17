package com.michaelbaranov.microba.gradienteditor.ui.basic;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.gradienteditor.GradientEditor;
import com.michaelbaranov.microba.gradienteditor.ui.GradientEditorLayout;
import com.michaelbaranov.microba.gradienteditor.ui.GradientEditorUI;
import com.michaelbaranov.microba.marker.MarkerBar;
import com.michaelbaranov.microba.marker.MarkerMutationModel;

public class BasicGradientEditorUI extends GradientEditorUI {

	protected GradientBar gradientBar;

	protected MarkerBar colorBar;

	protected MarkerBar alphaBar;

	private GradientEditor gradient;

	private GradientEditorListener editorListener;

	public static ComponentUI createUI(JComponent c) {
		return new BasicGradientEditorUI();
	}

	public void installUI(JComponent component) {
		gradient = (GradientEditor) component;
		editorListener = new GradientEditorListener();
		createAndConfigureSubcomponents();
		installSubcomponents();
		installListeners();
		gradient.revalidate();
	}

	public void uninstallUI(JComponent component) {
		// JGradient gradient = (JGradient) component;
		uninstallSubcomponents();
		uninstallListeners();
	}

	protected void installSubcomponents() {
		gradient.setLayout(new GradientEditorLayout(colorBar, gradientBar));
		gradient.add(colorBar);
		gradient.add(gradientBar);
	}

	protected void uninstallSubcomponents() {
		gradient.setLayout(new FlowLayout());
		gradient.remove(colorBar);
		gradient.remove(gradientBar);
	}

	private void installListeners() {
		gradient.addPropertyChangeListener(editorListener);
	}

	private void uninstallListeners() {
		gradient.removePropertyChangeListener(editorListener);
	}

	private void createAndConfigureSubcomponents() {
		gradientBar = new GradientBar(gradient.getDataModel());
		gradientBar.setOrientation(gradient.getOrientation());
		gradientBar.setColorPositionColumn(gradient.getColorPositionColumn());
		gradientBar.setColorColumn(gradient.getColorColumn());

		colorBar = new MarkerBar(gradient.getDataModel(), gradient
				.getColorSelectionModel());
		colorBar.setOrientation(gradient.getOrientation());
		colorBar.setMutationModel(gradient.getColorMutationModel());
		colorBar.setPositionColumn(gradient.getColorPositionColumn());
		colorBar.setColorColumn(gradient.getColorColumn());

		alphaBar = new MarkerBar(gradient.getDataModel(), gradient
				.getColorSelectionModel());
		alphaBar.setOrientation(gradient.getOrientation());
		alphaBar.setMutationModel(gradient.getColorMutationModel());
		alphaBar.setPositionColumn(gradient.getColorPositionColumn());
		alphaBar.setColorColumn(gradient.getColorColumn());
	}

	class GradientEditorListener implements PropertyChangeListener {

		public void propertyChange(PropertyChangeEvent evt) {
			if (GradientEditor.PROPERTY_DATA_MODEL
					.equals(evt.getPropertyName())) {
				gradientBar.setDataModel((BoundedTableModel) evt.getNewValue());
				colorBar.setDataModel((BoundedTableModel) evt.getNewValue());
			}
			if (GradientEditor.PROPERTY_COLOR_SELECTION_MODEL.equals(evt
					.getPropertyName())) {
				colorBar.setSelectionModel((ListSelectionModel) evt
						.getNewValue());
			}
			if (GradientEditor.PROPERTY_COLOR_MUTATION_MODEL.equals(evt
					.getPropertyName())) {
				colorBar.setMutationModel((MarkerMutationModel) evt
						.getNewValue());
			}
			if (GradientEditor.PROPERTY_COLOR_POSITION_COLUMN.equals(evt
					.getPropertyName())) {
				colorBar.setPositionColumn(((Integer) evt.getNewValue())
						.intValue());
				gradientBar
						.setColorPositionColumn(((Integer) evt.getNewValue())
								.intValue());
			}
			if (GradientEditor.PROPERTY_COLOR_COLUMN.equals(evt
					.getPropertyName())) {
				gradientBar.setColorColumn(((Integer) evt.getNewValue())
						.intValue());
				colorBar.setColorColumn(((Integer) evt.getNewValue())
						.intValue());

			}
			if (GradientEditor.PROPERTY_ORIENTATION.equals(evt
					.getPropertyName())) {
				colorBar.setOrientation(((Integer) evt.getNewValue())
						.intValue());
				gradientBar.setOrientation(((Integer) evt.getNewValue())
						.intValue());
			}
			if ("enabled".equals(evt.getPropertyName())) {
				colorBar.setEnabled(((Boolean) evt.getNewValue())
						.booleanValue());
				gradientBar.setEnabled(((Boolean) evt.getNewValue())
						.booleanValue());
			}

		}

	}

	public Dimension getMinimumSize(JComponent c) {
		GradientBar gradient = (GradientBar) c;

		Dimension minimumSize = gradientBar.getMinimumSize();
		Dimension minimumSize2 = colorBar.getMinimumSize();
		if (gradient.getOrientation() == SwingConstants.HORIZONTAL)
			return new Dimension(Math
					.max(minimumSize.width, minimumSize2.width),
					minimumSize.height + minimumSize2.height);
		else
			return new Dimension(minimumSize.width + minimumSize2.width, Math
					.max(minimumSize.height, minimumSize2.height));

	}

	public Dimension getPreferredSize(JComponent c) {
		Dimension preferredSize = gradientBar.getPreferredSize();
		Dimension preferredSize2 = colorBar.getPreferredSize();
		if (gradient.getOrientation() == SwingConstants.HORIZONTAL)
			return new Dimension(Math.max(preferredSize.width,
					preferredSize2.width), preferredSize.height
					+ preferredSize2.height);
		else
			return new Dimension(preferredSize.width + preferredSize2.width,
					Math.max(preferredSize.height, preferredSize2.height));
	}

}
