package com.michaelbaranov.microba.gradienteditor;

import javax.swing.JColorChooser;
import javax.swing.ListSelectionModel;

import com.michaelbaranov.microba.gradient.GradientBar;
import com.michaelbaranov.microba.marker.MarkerBar;
import com.michaelbaranov.microba.marker.MarkerMutationModel;

/**
 * IMPORTANT: alpha featre not implemented. Stubs only. No alpha marker bar yet.
 * <p>
 * 
 * This is a component for displaying/modifying a gradient (palette).
 * 
 * <p>
 * Implementation details: <br>
 * This implementation combines a {@link GradientBar} with two {@link MarkerBar}
 * components. The marker bars are used to provide editing capabilities to the
 * gradient bar. Note, that this component doesn't provide direct
 * color-selecting capabilitied but relies on other external components such as
 * {@link JColorChooser}.
 * 
 * @author Michael Baranov
 * 
 */
public class GradientEditor extends GradientBar {

	/**
	 * The name of a "colorSelectionModel" property.
	 */
	public static final String PROPERTY_COLOR_SELECTION_MODEL = "colorSelectionModel";

	/**
	 * The name of a "alphaSelectionModel" property.
	 */
	public static final String PROPERTY_ALPHA_SELECTION_MODEL = "alphaSelectionModel";

	/**
	 * The name of a "colorMutationModel" property.
	 */
	public static final String PROPERTY_COLOR_MUTATION_MODEL = "colorMutationModel";

	/**
	 * The name of a "alphaMutationModel" property.
	 */
	public static final String PROPERTY_ALPHA_MUTATION_MODEL = "alphaMutationModel";

	private static final String uiClassID = "microba.GradientEditorUI";

	private ListSelectionModel colorSelectionModel = null;

	private ListSelectionModel alphaSelectionModel = null;

	private MarkerMutationModel colorMutationModel = null;

	private MarkerMutationModel alphaMutationModel = null;

	/**
	 * Constructor.
	 */
	public GradientEditor() {
		super();
		DefaultGradientEditorModel defaultGradientEditorModel = new DefaultGradientEditorModel();
		dataModel = defaultGradientEditorModel;
		colorSelectionModel = defaultGradientEditorModel;
		colorMutationModel = defaultGradientEditorModel;
		setFocusable(true);
		updateUI();
	}

	public String getUIClassID() {
		return uiClassID;
	}

	/**
	 * Regturns the current color mutation model.
	 * 
	 * @return current color mutation model
	 * @see #setColorMutationModel(MarkerMutationModel)
	 * @see MarkerMutationModel
	 */
	public MarkerMutationModel getColorMutationModel() {
		return colorMutationModel;
	}

	/**
	 * Replaces current color mutation model with given one.
	 * 
	 * @param mutationModel
	 *            new mutation model. May be <code>null<code>. 
	 * @see #getColorMutationModel()
	 * @see MarkerMutationModel
	 */
	public void setColorMutationModel(MarkerMutationModel mutationModel) {
		MarkerMutationModel oldMutationModel = this.colorMutationModel;
		this.colorMutationModel = mutationModel;
		firePropertyChange(PROPERTY_COLOR_MUTATION_MODEL, oldMutationModel, mutationModel);
	}

	/**
	 * Returns current color selection model.
	 * 
	 * @return current color selection model.
	 * @see #setColorSelectionModel(ListSelectionModel)
	 */
	public ListSelectionModel getColorSelectionModel() {
		return colorSelectionModel;
	}

	/**
	 * Replaces current color selection model with given one. This
	 * implementation uses
	 * <code>{@link ListSelectionModel#getLeadSelectionIndex()}</code> to
	 * determine selected marker.
	 * 
	 * @param selectionModel
	 *            new selection model. May be <code>null<code>.
	 *            
	 * @see #getColorSelectionModel()
	 */
	public void setColorSelectionModel(ListSelectionModel selectionModel) {
		ListSelectionModel oldSelectionModel = this.colorSelectionModel;
		this.colorSelectionModel = selectionModel;
		firePropertyChange(PROPERTY_COLOR_SELECTION_MODEL, oldSelectionModel,
				selectionModel);
	}

	/**
	 * Returns current alpha selection model.
	 * 
	 * @return current alpha selection model.
	 * @see #setAlphaSelectionModel(ListSelectionModel)
	 */
	public ListSelectionModel getAlphaSelectionModel() {
		return alphaSelectionModel;
	}

	/**
	 * Replaces current alpha selection model with given one. This
	 * implementation uses
	 * <code>{@link ListSelectionModel#getLeadSelectionIndex()}</code> to
	 * determine selected marker.
	 * 
	 * @param selectionModel
	 *            new selection model. May be <code>null<code>.
	 *            
	 * @see #getAlphaSelectionModel()
	 */
	public void setAlphaSelectionModel(ListSelectionModel selectionModel) {
		this.alphaSelectionModel = selectionModel;
	}

	/**
	 * Regturns the current alpha mutation model.
	 * 
	 * @return current alpha mutation model
	 * @see #setAlphaMutationModel(MarkerMutationModel)
	 * @see MarkerMutationModel
	 */
	public MarkerMutationModel getAlphaMutationModel() {
		return alphaMutationModel;
	}

	/**
	 * Replaces current alpha mutation model with given one.
	 * 
	 * @param mutationModel
	 *            new mutation model. May be <code>null<code>. 
	 * @see #getAlphaMutationModel()
	 * @see MarkerMutationModel
	 */
	public void setAlphaMutationModel(MarkerMutationModel mutationModel) {
		this.alphaMutationModel = mutationModel;
	}

}
