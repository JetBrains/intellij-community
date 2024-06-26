package com.michaelbaranov.microba.common;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.util.EventListener;

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

  private final DefaultListSelectionModel selection = new DefaultListSelectionModel();

  public AbstractBoundedTableModelWithSelection() {
    super();
  }

  @Override
  public void addListSelectionListener(ListSelectionListener l) {
    selection.addListSelectionListener(l);
  }

  @Override
  public void addSelectionInterval(int index0, int index1) {
    selection.addSelectionInterval(index0, index1);
  }

  @Override
  public void clearSelection() {
    selection.clearSelection();
  }

  @Override
  public int getAnchorSelectionIndex() {
    return selection.getAnchorSelectionIndex();
  }

  @Override
  public int getLeadSelectionIndex() {
    return selection.getLeadSelectionIndex();
  }

  @Override
  public EventListener[] getListeners(Class listenerType) {
    return selection.getListeners(listenerType);
  }

  public ListSelectionListener[] getListSelectionListeners() {
    return selection.getListSelectionListeners();
  }

  @Override
  public int getMaxSelectionIndex() {
    return selection.getMaxSelectionIndex();
  }

  @Override
  public int getMinSelectionIndex() {
    return selection.getMinSelectionIndex();
  }

  @Override
  public int getSelectionMode() {
    return selection.getSelectionMode();
  }

  @Override
  public boolean getValueIsAdjusting() {
    return selection.getValueIsAdjusting();
  }

  @Override
  public void insertIndexInterval(int index, int length, boolean before) {
    selection.insertIndexInterval(index, length, before);
  }

  public boolean isLeadAnchorNotificationEnabled() {
    return selection.isLeadAnchorNotificationEnabled();
  }

  @Override
  public boolean isSelectedIndex(int index) {
    return selection.isSelectedIndex(index);
  }

  @Override
  public boolean isSelectionEmpty() {
    return selection.isSelectionEmpty();
  }

  // J2SE5
  // ////////////////////////////////////////////////////
  // public void moveLeadSelectionIndex(int leadIndex) {
  // selection.moveLeadSelectionIndex(leadIndex);
  // }

  @Override
  public void removeIndexInterval(int index0, int index1) {
    selection.removeIndexInterval(index0, index1);
  }

  @Override
  public void removeListSelectionListener(ListSelectionListener l) {
    selection.removeListSelectionListener(l);
  }

  @Override
  public void removeSelectionInterval(int index0, int index1) {
    selection.removeSelectionInterval(index0, index1);
  }

  @Override
  public void setAnchorSelectionIndex(int anchorIndex) {
    selection.setAnchorSelectionIndex(anchorIndex);
  }

  public void setLeadAnchorNotificationEnabled(boolean flag) {
    selection.setLeadAnchorNotificationEnabled(flag);
  }

  @Override
  public void setLeadSelectionIndex(int leadIndex) {
    selection.setLeadSelectionIndex(leadIndex);
  }

  @Override
  public void setSelectionInterval(int index0, int index1) {
    selection.setSelectionInterval(index0, index1);
  }

  @Override
  public void setSelectionMode(int selectionMode) {
    selection.setSelectionMode(selectionMode);
  }

  @Override
  public void setValueIsAdjusting(boolean isAdjusting) {
    selection.setValueIsAdjusting(isAdjusting);
  }

}
