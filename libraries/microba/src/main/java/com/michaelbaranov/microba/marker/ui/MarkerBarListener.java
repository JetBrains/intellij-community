package com.michaelbaranov.microba.marker.ui;

import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import com.michaelbaranov.microba.common.BoundedTableModel;
import com.michaelbaranov.microba.marker.MarkerBar;
import com.michaelbaranov.microba.marker.MarkerMutationModel;
import com.michaelbaranov.microba.marker.ui.basic.BasicMarkerBarUI;

public class MarkerBarListener implements TableModelListener, MouseListener,
		MouseMotionListener, FocusListener, PropertyChangeListener,
		ComponentListener, ListSelectionListener {

	private static final String VK_DELETE_KEY = "##VK_DELETE##";

	private static final KeyStroke DELETE_KEYSTROKE = KeyStroke.getKeyStroke(
			KeyEvent.VK_DELETE, 0);

	private final BasicMarkerBarUI barUI;

	private MarkerBar bar;

	private int holdingIndex = -1;

	private int holdingShift;

	public MarkerBarListener(BasicMarkerBarUI barUI, MarkerBar markerBar) {
		this.barUI = barUI;
		this.bar = markerBar;
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {

		if (!bar.isEnabled())
			return;

		this.barUI.calculateViewRectAndBaseline(bar);

		bar.requestFocusInWindow();

		BoundedTableModel dataModel = bar.getDataModel();
		ListSelectionModel selectionModel = bar.getSelectionModel();
		int dataColumn = bar.getPositionColumn();

		if (SwingUtilities.isLeftMouseButton(e) && selectionModel != null
				&& dataModel != null) {
			handleLMBDown(e, dataModel, selectionModel, dataColumn);
		}
		if (SwingUtilities.isRightMouseButton(e) && dataModel != null) {
			handleRMBDown(e, dataModel);
		}

	}

	private void handleRMBDown(MouseEvent e, BoundedTableModel dataModel) {
		int logicalOffset;
		if (bar.getOrientation() == SwingConstants.HORIZONTAL)
			logicalOffset = this.barUI.componentOffsetToLogicalOffset(e.getX(),
					dataModel);
		else
			logicalOffset = this.barUI.componentOffsetToLogicalOffset(e.getY(),
					dataModel);
		int logicalPos = dataModel.getLowerBound() + logicalOffset;

		if (logicalPos >= dataModel.getLowerBound()
				&& logicalPos <= dataModel.getUpperBound()) {

			MarkerMutationModel mutationModel = bar.getMutationModel();
			if (mutationModel != null)
				mutationModel.addMarkAtPosition(logicalPos);

		}
	}

	private void handleLMBDown(MouseEvent e, BoundedTableModel dataModel,
			ListSelectionModel selectionModel, int dataColumn) {

		int numAreas = dataModel.getRowCount();
		Polygon areas[] = this.barUI.calculateMarkerAreas(bar);

		// try already selected
		for (int i = 0; i < numAreas; i++) {
			Polygon p = areas[i];
			if (p.contains(e.getPoint()) && selectionModel.isSelectedIndex(i)
					&& dataModel.isCellEditable(i, dataColumn)) {
				holdingIndex = i;
				return;
			}
		}
		// try editable
		for (int i = 0; i < numAreas; i++) {
			Polygon p = areas[i];
			if (p.contains(e.getPoint())
					&& dataModel.isCellEditable(i, dataColumn)) {
				selectionModel.setSelectionInterval(i, i);
				holdingIndex = i;
				if (bar.getOrientation() == SwingConstants.HORIZONTAL)
					holdingShift = e.getX() - p.xpoints[0];
				else
					holdingShift = e.getY() - p.ypoints[0];
				return;
			}
		}
		// try other
		for (int i = 0; i < numAreas; i++) {
			Polygon p = areas[i];
			if (p.contains(e.getPoint())) {
				selectionModel.setSelectionInterval(i, i);
				holdingIndex = -1;
				return;
			}
		}
		selectionModel.clearSelection();
	}

	public void mouseReleased(MouseEvent e) {
		holdingIndex = -1;

	}

	public void mouseEntered(MouseEvent e) {

	}

	public void mouseExited(MouseEvent e) {

	}

	public void mouseDragged(MouseEvent e) {

		if (!bar.isEnabled())
			return;

		BoundedTableModel dataModel = bar.getDataModel();
		if (holdingIndex >= 0 && dataModel != null) {
			int componentOffset;
			if (bar.getOrientation() == SwingConstants.HORIZONTAL)
				componentOffset = e.getX() - holdingShift;
			else
				componentOffset = e.getY() - holdingShift;

			int logicalOffset = this.barUI.componentOffsetToLogicalOffset(
					componentOffset, dataModel);
			int logicalPos = dataModel.getLowerBound() + logicalOffset;

			if (logicalPos < dataModel.getLowerBound())
				logicalPos = dataModel.getLowerBound();
			if (logicalPos > dataModel.getUpperBound())
				logicalPos = dataModel.getUpperBound();

			dataModel.setValueAt(new Integer(logicalPos), holdingIndex, bar
					.getPositionColumn());
		}

	}

	public void mouseMoved(MouseEvent e) {

	}

	public void focusGained(FocusEvent e) {
		bar.repaint();

	}

	public void focusLost(FocusEvent e) {
		bar.repaint();

	}

	public void propertyChange(PropertyChangeEvent evt) {

		if (evt.getSource() instanceof MarkerBar) {

			if (MarkerBar.PROPERTY_DATA_MODEL.equals(evt.getPropertyName())) {
				BoundedTableModel oldModel = (BoundedTableModel) evt
						.getOldValue();
				BoundedTableModel newModel = (BoundedTableModel) evt
						.getNewValue();

				if (oldModel != null)
					oldModel.removeTableModelListener(this);
				if (newModel != null)
					newModel.addTableModelListener(this);

				holdingIndex = -1;
				bar.revalidate();
			}
			if (MarkerBar.PROPERTY_SELECTION_MODEL.equals(evt
					.getPropertyName())) {
				ListSelectionModel oldModel = (ListSelectionModel) evt
						.getOldValue();
				ListSelectionModel newModel = (ListSelectionModel) evt
						.getNewValue();

				if (oldModel != null)
					oldModel.removeListSelectionListener(this);
				if (newModel != null)
					newModel.addListSelectionListener(this);

				holdingIndex = -1;
				bar.repaint();
			}
			if (MarkerBar.PROPERTY_MUTATION_MODEL
					.equals(evt.getPropertyName())) {
				// do not care
			}
			if (MarkerBar.PROPERTY_ORIENTATION.equals(evt.getPropertyName())) {
				holdingIndex = -1;
				bar.revalidate();
			}
			if (MarkerBar.PROPERTY_POSITION_COLUMN.equals(evt
					.getPropertyName())) {
				holdingIndex = -1;
				bar.repaint();
			}
			if ("enabled".equals(evt.getPropertyName())) {
				holdingIndex = -1;
				bar.repaint();
			}
		}
		if (evt.getSource() instanceof BoundedTableModel) {
			bar.revalidate();
		}

	}

	public void installKeyboardActions(JComponent c) {

		c.getInputMap().put(DELETE_KEYSTROKE, VK_DELETE_KEY);
		c.getActionMap().put(VK_DELETE_KEY, new AbstractAction() {

			public void actionPerformed(ActionEvent e) {

				BoundedTableModel dataModel = bar.getDataModel();
				ListSelectionModel selectionModel = bar.getSelectionModel();
				MarkerMutationModel mutationModel = bar.getMutationModel();

				if (selectionModel != null && dataModel != null
						&& mutationModel != null
						&& !selectionModel.isSelectionEmpty()) {
					int selected = selectionModel.getLeadSelectionIndex();
					if (dataModel.isCellEditable(selected, bar
							.getPositionColumn())) {
						mutationModel.removeMarkerAtIndex(selected);
					}
				}
			}
		});
	}

	public void uninstallKeyboardActions(JComponent c) {
		c.getActionMap().remove(VK_DELETE_KEY);
		c.getInputMap().remove(DELETE_KEYSTROKE);
	}

	public void componentResized(ComponentEvent e) {

	}

	public void componentMoved(ComponentEvent e) {

	}

	public void componentShown(ComponentEvent e) {

	}

	public void componentHidden(ComponentEvent e) {

	}

	public void tableChanged(TableModelEvent e) {
		bar.repaint();

	}

	public void valueChanged(ListSelectionEvent e) {
		bar.repaint();

	}
}