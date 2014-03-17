package com.michaelbaranov.microba.common;

import java.util.EventListener;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;

/**
 * A simple abstract implementation of <code>BoundedTableModel</code> with
 * implemented <code>ListSelectionModel</code> functionality. A convenience
 * class.
 * 
 * @author Michael Baranov
 * 
 */
public abstract class AbstractBoundedTableModelWithSelection extends
		AbstractBoundedTableModel implements ListSelectionModel {

	private DefaultListSelectionModel selection = new DefaultListSelectionModel();

	public AbstractBoundedTableModelWithSelection() {
		super();
	}

	public void addListSelectionListener(ListSelectionListener l) {
		selection.addListSelectionListener(l);
	}

	public void addSelectionInterval(int index0, int index1) {
		selection.addSelectionInterval(index0, index1);
	}

	public void clearSelection() {
		selection.clearSelection();
	}

	public int getAnchorSelectionIndex() {
		return selection.getAnchorSelectionIndex();
	}

	public int getLeadSelectionIndex() {
		return selection.getLeadSelectionIndex();
	}

	public EventListener[] getListeners(Class listenerType) {
		return selection.getListeners(listenerType);
	}

	public ListSelectionListener[] getListSelectionListeners() {
		return selection.getListSelectionListeners();
	}

	public int getMaxSelectionIndex() {
		return selection.getMaxSelectionIndex();
	}

	public int getMinSelectionIndex() {
		return selection.getMinSelectionIndex();
	}

	public int getSelectionMode() {
		return selection.getSelectionMode();
	}

	public boolean getValueIsAdjusting() {
		return selection.getValueIsAdjusting();
	}

	public void insertIndexInterval(int index, int length, boolean before) {
		selection.insertIndexInterval(index, length, before);
	}

	public boolean isLeadAnchorNotificationEnabled() {
		return selection.isLeadAnchorNotificationEnabled();
	}

	public boolean isSelectedIndex(int index) {
		return selection.isSelectedIndex(index);
	}

	public boolean isSelectionEmpty() {
		return selection.isSelectionEmpty();
	}

	// J2SE5
	// ////////////////////////////////////////////////////
	// public void moveLeadSelectionIndex(int leadIndex) {
	// selection.moveLeadSelectionIndex(leadIndex);
	// }

	public void removeIndexInterval(int index0, int index1) {
		selection.removeIndexInterval(index0, index1);
	}

	public void removeListSelectionListener(ListSelectionListener l) {
		selection.removeListSelectionListener(l);
	}

	public void removeSelectionInterval(int index0, int index1) {
		selection.removeSelectionInterval(index0, index1);
	}

	public void setAnchorSelectionIndex(int anchorIndex) {
		selection.setAnchorSelectionIndex(anchorIndex);
	}

	public void setLeadAnchorNotificationEnabled(boolean flag) {
		selection.setLeadAnchorNotificationEnabled(flag);
	}

	public void setLeadSelectionIndex(int leadIndex) {
		selection.setLeadSelectionIndex(leadIndex);
	}

	public void setSelectionInterval(int index0, int index1) {
		selection.setSelectionInterval(index0, index1);
	}

	public void setSelectionMode(int selectionMode) {
		selection.setSelectionMode(selectionMode);
	}

	public void setValueIsAdjusting(boolean isAdjusting) {
		selection.setValueIsAdjusting(isAdjusting);
	}

}
