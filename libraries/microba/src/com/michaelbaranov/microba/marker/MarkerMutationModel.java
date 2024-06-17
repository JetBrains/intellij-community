package com.michaelbaranov.microba.marker;

/**
 * A callback interface used exclusively by
 * <code>{@link com.michaelbaranov.microba.marker.MarkerBar}</code> as a
 * mutation model.
 * <p>
 * The <code>JMarkerBar</code> class notifies an implementor of the
 * <code>MutationModel</code> about the fact, that the user is requesting a
 * marker to be removed or added.
 * 
 * @author Michael Baranov
 * @see com.michaelbaranov.microba.marker.MarkerBar
 */
public interface MarkerMutationModel {

	/**
	 * Called when the user requests a mark to be removed from
	 * <code>JMarkerBar</code>.
	 * 
	 * @param index
	 *            index of the mark to be removed.
	 */
	void removeMarkerAtIndex(int index);

	/**
	 * Called when the user requests a mark to be inserted into
	 * <code>JMarkerBar</code>.
	 * 
	 * @param position
	 *            position at which to insert the mark.
	 * @return index of newly added mark.
	 */
	int addMarkAtPosition(int position);
}
