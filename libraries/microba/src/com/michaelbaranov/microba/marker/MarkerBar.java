package com.michaelbaranov.microba.marker;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.common.MicrobaComponent;
import com.michaelbaranov.microba.marker.ui.MarkerBarUI;

/**
 * A bar with multiple draggable position marks.
 * <p>
 * <strong>Features:</strong>
 * <ul>
 * <li>Single marker selection.</li>
 * <li>Respects unmovable marks.</li>
 * <li>Mouse marker selection & dragging.</li>
 * <li>Hirizontal & verical orientation.</li>
 * <li>Supported L&F: Metal, Windows, Motif, Basic for others.</li>
 * </ul>
 * 
 * <strong>Implementation details:</strong>
 * <ul>
 * <li>Data model:
 * <code>{@link com.michaelbaranov.microba.common.BoundedTableModel}</code></li>
 * <li>Selection model: <code>{@link javax.swing.ListSelectionModel}</code></li>
 * <li>MutationModel model:
 * <code>{@link com.michaelbaranov.microba.marker.MarkerMutationModel}</code></li>
 * <li>UI delegate:
 * <code>{@link com.michaelbaranov.microba.marker.ui.MarkerBarUI}</code></li>
 * </ul>
 * <p>
 * 
 * This implementation queries marker positions from a single table column of
 * the data model. The index defaults to 0, but you can specify it with <code>
 * {@link #setPositionColumn(int)}</code>.
 * A marker is considered to be unmovable, if corresponding table cell of the
 * data model is reported to be uneditable.
 * <p>
 * 
 * This implementation determines the only currently selected marker with <code>
 * {@link ListSelectionModel#getLeadSelectionIndex()}</code>
 * of the selection model, so current selection model's selection mode has no
 * effect.
 * 
 * @author Michael Baranov
 * 
 */
public class MarkerBar extends MicrobaComponent {

	/**
	 * The name of a "dataModel" property.
	 */
	public static final String PROPERTY_DATA_MODEL = "dataModel";

	/**
	 * The name of a "selectionModel" property.
	 */
	public static final String PROPERTY_SELECTION_MODEL = "selectionModel";

	/**
	 * The name of a "mutationModel" property.
	 */
	public static final String PROPERTY_MUTATION_MODEL = "mutationModel";

	/**
	 * The name of a "orientation" property.
	 */
	public static final String PROPERTY_ORIENTATION = "orientation";

	/**
	 * The name of a "positionColumn" property.
	 */
	public static final String PROPERTY_POSITION_COLUMN = "positionColumn";

	/**
	 * The name of a "colorColumn" property.
	 */
	public static final String PROPERTY_COLOR_COLUMN = "colorColumn";

	/**
	 * The name of a "fliped" property.
	 */
	public static final String PROPERTY_FLIP = "fliped";

	private static final String uiClassID = "microba.MarkerBarUI";

	private BoundedTableModel dataModel = null;

	private ListSelectionModel selectionModel = null;

	private MarkerMutationModel mutationModel = null;

	private int positionColumn = 0;

	private int colorColumn = -1;

	private int orientation = SwingConstants.HORIZONTAL;

	private boolean fliped = false;

	/**
	 * Constructs a <code>MarkerBar</code> with all models set to a single
	 * <code>DefaultMarkerModel.</code>
	 * 
	 * @see DefaultMarkerModel
	 */
	public MarkerBar() {
		super();
		DefaultMarkerModel markerModel = new DefaultMarkerModel();
		dataModel = markerModel;
		selectionModel = markerModel;
		mutationModel = markerModel;
		setFocusable(true);
		updateUI();
	}

	/**
	 * Constructs a <code>MarkerBar</code> with given orientation. All models
	 * set to a single <code>DefaultMarkerModel</code>.
	 * 
	 * @param orientation
	 *            initial orientation. Possible values:
	 *            <code>SwingConstants.HORIZONTAL</code> or
	 *            <code>SwingConstants.VERTICAL</code>
	 * @see DefaultMarkerModel
	 */
	public MarkerBar(int orientation) {
		super();
		DefaultMarkerModel markerModel = new DefaultMarkerModel();
		dataModel = markerModel;
		selectionModel = markerModel;
		mutationModel = markerModel;
		this.orientation = orientation;
		setFocusable(true);
		updateUI();
	}

	/**
	 * Constructs a <code>MarkerBar</code> with given data model, a
	 * <code>DefaultListSelectionModel<code> as selection model and no mutation model.
	 * 
	 * @param dataModel
	 *            initial data model. May be <code>null<code>
	 * 
	 * @see BoundedTableModel
	 */
	public MarkerBar(BoundedTableModel dataModel) {
		super();
		this.dataModel = dataModel;
		selectionModel = new DefaultListSelectionModel();
		mutationModel = null;
		setFocusable(true);
		updateUI();
	}

	/**
	 * Constructs a <code>MarkerBar</code> with given data model and selection
	 * model. No mutation model.
	 * 
	 * @param dataModel
	 *            initial data model. May be <code>null<code>;
	 * @param selectionModel initial selection model.
	 * @see BoundedTableModel
	 */
	public MarkerBar(BoundedTableModel dataModel, ListSelectionModel selectionModel) {
		super();
		this.dataModel = dataModel;
		this.selectionModel = selectionModel;
		mutationModel = null;
		setFocusable(true);
		updateUI();
	}

	/**
	 * Look&Feel UI delegate key (classID). This implementation returns:
	 * <code>"MarkerBarUI"<code>.
	 */
	public String getUIClassID() {
		return uiClassID;
	}

	/**
	 * Returns current data model.
	 * 
	 * @return current BoundedTableModel.
	 * @see #setDataModel(BoundedTableModel)
	 * @see BoundedTableModel
	 */
	public BoundedTableModel getDataModel() {
		return dataModel;
	}

	/**
	 * Replaces current data model with specified one. This implementation uses
	 * current position column index to query marker positions.
	 * 
	 * @param model
	 *            new data model.
	 * @see #getDataModel()
	 * @see #getPositionColumn()
	 * @see BoundedTableModel
	 */
	public void setDataModel(BoundedTableModel model) {
		BoundedTableModel oldModel = this.dataModel;
		this.dataModel = model;
		firePropertyChange(PROPERTY_DATA_MODEL, oldModel, model);
	}

	/**
	 * Returns current component orientation.
	 * 
	 * @return current component orientation.
	 * @see #setOrientation(int)
	 */
	public int getOrientation() {
		return orientation;
	}

	/**
	 * Re-orientates the component.
	 * 
	 * @param orientation
	 *            new orientation value. Possible values:
	 *            <code>{@link SwingConstants#HORIZONTAL}</code> or
	 *            <code>{@link SwingConstants#VERTICAL}</code>
	 * @see #getOrientation()
	 */
	public void setOrientation(int orientation) {
		int oldOrientation = this.orientation;
		this.orientation = orientation;
		firePropertyChange(PROPERTY_ORIENTATION, oldOrientation, orientation);
	}

	/**
	 * Returns current mutation model.
	 * 
	 * @return current MutationModel.
	 * @see #setMutationModel(MarkerMutationModel)
	 * @see MarkerMutationModel
	 */
	public MarkerMutationModel getMutationModel() {
		return mutationModel;
	}

	/**
	 * Replaces current mutation model with given one.
	 * 
	 * @param mutationModel
	 *            new mutation model. May be <code>null<code>. 
	 * @see #getMutationModel()
	 * @see MarkerMutationModel
	 */
	public void setMutationModel(MarkerMutationModel mutationModel) {
		MarkerMutationModel oldMutationModel = this.mutationModel;
		this.mutationModel = mutationModel;
		firePropertyChange(PROPERTY_MUTATION_MODEL, oldMutationModel, mutationModel);
	}

	/**
	 * Returns current selection model.
	 * 
	 * @return current ListSelectionModel.
	 * @see #setSelectionModel(ListSelectionModel)
	 */
	public ListSelectionModel getSelectionModel() {
		return selectionModel;
	}

	/**
	 * Replaces current selection model with given one. This implementation uses
	 * <code>{@link ListSelectionModel#getLeadSelectionIndex()}</code> to
	 * determine selected marker.
	 * 
	 * @param selectionModel
	 *            new selection model. May be <code>null<code>.
	 *            
	 * @see #getSelectionModel()
	 */
	public void setSelectionModel(ListSelectionModel selectionModel) {
		ListSelectionModel oldSelectionModel = this.selectionModel;
		this.selectionModel = selectionModel;
		firePropertyChange(PROPERTY_SELECTION_MODEL, oldSelectionModel, selectionModel);
	}

	/**
	 * Returns an index of currently used table column to query marker position.
	 * 
	 * @return current position column index.
	 * @see #setPositionColumn(int)
	 */
	public int getPositionColumn() {
		return positionColumn;
	}

	/**
	 * Sets the index of the data model table column used to query marker
	 * position.
	 * 
	 * @param positionColumn
	 *            new position column index.
	 * @see #getPositionColumn()
	 */
	public void setPositionColumn(int positionColumn) {
		int oldDataColumn = this.positionColumn;
		this.positionColumn = positionColumn;
		firePropertyChange(PROPERTY_POSITION_COLUMN, oldDataColumn, positionColumn);
	}

	/**
	 * Returns an index of currently used table column to query marker color.
	 * Defaults to -1, which means not to query data model for color.
	 * 
	 * @return current color column index.
	 * @see #setColorColumn(int)
	 */
	public int getColorColumn() {
		return colorColumn;
	}

	/**
	 * Sets the index of the data model table column used to query marker color.
	 * Set to -1 in order not to query data model for color data.
	 * 
	 * @param colorColumn
	 *            new color column index.
	 * @see #getColorColumn()
	 */
	public void setColorColumn(int colorColumn) {
		int old = this.colorColumn;
		this.colorColumn = colorColumn;
		firePropertyChange(PROPERTY_COLOR_COLUMN, old, colorColumn);
	}

	/**
	 * Returns a distance in pixeld between the edge of the component (left &
	 * right edge for horizontal orientation, top & bottom edge for vertical)
	 * and a marker beak point in outermost position. The value is actually
	 * queried from current UI delegate.
	 * 
	 * @return Gap value.
	 * @see MarkerBarUI
	 */
	public int getMarkerSideGap() {
		return ((MarkerBarUI) getUI()).getMarkerSideGap();
	}

	/**
	 * Returns current flip flag value.
	 * <p>
	 * The flip flag defines where marker bicks are pointed:<br>
	 * <code>true</code>: down for horizontal orientation, left for vertical.<br>
	 * <code>false</code>: up for horizontal orientation, right for vertical.
	 * 
	 * @return current flip value.
	 */
	public boolean isFliped() {
		return fliped;
	}

	/**
	 * Set flip flag value.
	 * <p>
	 * The flip flag defines where marker bicks are pointed:<br>
	 * <code>true</code>: down for horizontal orientation, left for vertical.<br>
	 * <code>false</code>: up for horizontal orientation, right for vertical.
	 * 
	 * @param flip
	 *            new flip flag value.
	 */
	public void setFliped(boolean flip) {
		boolean old = this.fliped;
		this.fliped = flip;
		firePropertyChange(PROPERTY_FLIP, old, flip);
	}

}
