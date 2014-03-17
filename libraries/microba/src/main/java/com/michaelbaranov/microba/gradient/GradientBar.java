package com.michaelbaranov.microba.gradient;

import java.awt.Color;

import javax.swing.SwingConstants;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.common.MicrobaComponent;

/**
 * IMPORTANT: alpha featre not implemented. Stubs only.
 * <p>
 * 
 * A concrete implementation of JComponent. Displays an area filled with
 * gradient color (palette). The color and alpha value (transparency) is
 * linearly interpolated between key points.
 * <p>
 * The color key points are provided by the data model in combination with color
 * column (index) and color position column (index). Each row in the model
 * represents a colored key point. Color values are expected to be of type
 * {@link Color} and position values of type {@link Number} with integer
 * precision.
 * <p>
 * The alpha key points are provided by the alpha model in combination with
 * alpha column (index) and alpha position column (index). Each row in the model
 * represents an alpha key point. Alpha values are expected to be of type
 * {@link Number} with floating-point precision ranging form 0.0f (transparent)
 * to 1.0f (opaque) and position values of type {@link Number} with integer
 * precision.
 * 
 * Example:
 * 
 * <pre>
 * GradientBar bar = new GradientBar();
 * bar.setDataModel(myColorModel);
 * bar.setColorColumn(0);
 * bar.setColorPositionColumn(1);
 * 
 * bar.setAlphaModel(myAlphaModel);
 * bar.setAlphaColumn(1);
 * bar.setAlphaPositionColumn(0);
 * </pre>
 * 
 * @author Michael Baranov
 * 
 */
public class GradientBar extends MicrobaComponent {

	/**
	 * The name of a "dataModel" property.
	 */
	public static final String PROPERTY_DATA_MODEL = "dataModel";

	/**
	 * The name of a "alphaModel" property.
	 */
	public static final String PROPERTY_ALPHA_MODEL = "alphaModel";

	/**
	 * The name of a "colorPositionColumn" property.
	 */
	public static final String PROPERTY_COLOR_POSITION_COLUMN = "colorPositionColumn";

	/**
	 * The name of a "alphaPositionColumn" property.
	 */
	public static final String PROPERTY_ALPHA_POSITION_COLUMN = "alphaPositionColumn";

	/**
	 * The name of a "colorColumn" property.
	 */
	public static final String PROPERTY_COLOR_COLUMN = "colorColumn";

	/**
	 * The name of a "alphaColumn" property.
	 */
	public static final String PROPERTY_ALPHA_COLUMN = "alphaColumn";

	/**
	 * The name of a "orientation" property.
	 */
	public static final String PROPERTY_ORIENTATION = "orientation";

	private static final String uiClassID = "microba.GradientUI";

	protected BoundedTableModel dataModel;

	protected BoundedTableModel alphaModel;

	protected int colorPositionColumn = 0;

	protected int alphaPositionColumn = 0;

	protected int colorColumn = 1;

	protected int alphaColumn = 1;

	protected int orientation = SwingConstants.HORIZONTAL;

	public String getUIClassID() {
		return uiClassID;
	}

	/**
	 * Constructor.
	 */
	public GradientBar() {
		super();
		dataModel = new DefaultGradientModel();
		setFocusable(false);
		updateUI();
	}

	/**
	 * Constructor.
	 */
	public GradientBar(BoundedTableModel model) {
		super();
		dataModel = model;
		setFocusable(false);
		updateUI();
	}

	/**
	 * Constructor.
	 */
	public GradientBar(BoundedTableModel model, int orientation) {
		super();
		dataModel = model;
		this.orientation = orientation;
		setFocusable(false);
		updateUI();
	}

	/**
	 * Returns the index of the color column for the data model.
	 * 
	 * @return index of color column
	 * @see #setColorColumn(int)
	 */
	public int getColorColumn() {
		return colorColumn;
	}

	/**
	 * Sets the index of the color column for the data model.
	 * 
	 * @param colorColumn
	 *            index of color column
	 * @see #getColorColumn()
	 */
	public void setColorColumn(int colorColumn) {
		int old = this.colorColumn;
		this.colorColumn = colorColumn;
		firePropertyChange(PROPERTY_COLOR_COLUMN, old, colorColumn);
	}

	/**
	 * Returns the index of the alpha column for the alpha model.
	 * 
	 * @return index of alpha column
	 * @see #setAlphaColumn(int)
	 */
	public int getAlphaColumn() {
		return alphaColumn;
	}

	/**
	 * Sets the index of the alpha column for the alpha model.
	 * 
	 * @param alphaColumn
	 *            index of alpha column
	 * @see #getAlphaColumn()
	 */
	public void setAlphaColumn(int alphaColumn) {
		int old = this.colorColumn;
		this.alphaColumn = alphaColumn;
		firePropertyChange(PROPERTY_ALPHA_COLUMN, old, alphaColumn);
	}

	/**
	 * Regturns the current data model. The data model provides key points for
	 * interpolation (position & color).
	 * 
	 * @return current data model
	 * @see #setDataModel(BoundedTableModel)
	 * @see #getColorColumn()
	 */
	public BoundedTableModel getDataModel() {
		return dataModel;
	}

	/**
	 * Sets the current data model. The data model provides key points for
	 * interpolation (position & color).
	 * 
	 * @param dataModel
	 *            current data model
	 * @see #getDataModel()
	 * @see #getColorColumn()
	 */
	public void setDataModel(BoundedTableModel dataModel) {
		BoundedTableModel old = this.dataModel;
		this.dataModel = dataModel;
		firePropertyChange(PROPERTY_DATA_MODEL, old, dataModel);
	}

	/**
	 * Regturns the current alpha model. The data model provides alpha key
	 * points for interpolation (position & alpha).
	 * 
	 * @return current alpha model
	 * @see #setAlphaModel(BoundedTableModel)
	 * @see #getAlphaColumn()
	 */
	public BoundedTableModel getAlphaModel() {
		return alphaModel;
	}

	/**
	 * Sets the current alpha model. The alpha model provides alpha key points
	 * for interpolation (position & alpha).
	 * 
	 * @param alphaModel
	 *            current alpha model
	 * @see #getAlphaModel()
	 * @see #getAlphaColumn()
	 */
	public void setAlphaModel(BoundedTableModel alphaModel) {
		BoundedTableModel old = this.alphaModel;
		this.alphaModel = alphaModel;
		firePropertyChange(PROPERTY_ALPHA_MODEL, old, alphaModel);
	}

	/**
	 * Returns current orientation of the component. Possible values are:
	 * <ul>
	 * <li>SwingConstants.HORIZONTAL
	 * <li>SwingConstants.VERTICAL
	 * </ul>
	 * 
	 * @return orientation
	 * @see #setOrientation(int)
	 */
	public int getOrientation() {
		return orientation;
	}

	/**
	 * Sets orientation of the component. Possible values are:
	 * <ul>
	 * <li>SwingConstants.HORIZONTAL
	 * <li>SwingConstants.VERTICAL
	 * </ul>
	 * 
	 * @param orientation
	 */
	public void setOrientation(int orientation) {
		int old = this.orientation;
		this.orientation = orientation;
		firePropertyChange(PROPERTY_ORIENTATION, old, orientation);
	}

	/**
	 * Returns the index of the position column for the data model.
	 * 
	 * @return index of position column
	 * @see #setColorPositionColumn(int)
	 */
	public int getColorPositionColumn() {
		return colorPositionColumn;
	}

	/**
	 * Sets the index of the position column for the data model.
	 * 
	 * @param positionColumn
	 *            index of position column
	 * @see #getColorPositionColumn()
	 */
	public void setColorPositionColumn(int positionColumn) {
		int old = this.colorPositionColumn;
		this.colorPositionColumn = positionColumn;
		firePropertyChange(PROPERTY_COLOR_POSITION_COLUMN, old, positionColumn);
	}

	/**
	 * Returns the index of the position column for the alpha model.
	 * 
	 * @return index of position column
	 * @see #setAlphaPositionColumn(int)
	 */
	public int getAlphaPositionColumn() {
		return alphaPositionColumn;
	}

	/**
	 * Sets the index of the position column for the alpha model.
	 * 
	 * @param positionColumn
	 *            index of position column
	 * @see #getAlphaPositionColumn()
	 */
	public void setAlphaPositionColumn(int positionColumn) {
		int old = this.alphaPositionColumn;
		this.alphaPositionColumn = positionColumn;
		firePropertyChange(PROPERTY_ALPHA_POSITION_COLUMN, old, positionColumn);
	}

}
