package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public class KeyProcessorContext {
  private final ArrayList<AnAction> myActions = new ArrayList<AnAction>();
  private JComponent myFoundComponent;
  private boolean myHasSecondStroke;

  private DataContext myDataContext;
  private boolean isModalContext;
  Component myFocusOwner;
  private KeyEvent myInputEvent;

  public ArrayList<AnAction> getActions() {
    return myActions;
  }

  public JComponent getFoundComponent() {
    return myFoundComponent;
  }

  public void setFoundComponent(final JComponent foundComponent) {
    myFoundComponent = foundComponent;
  }

  public void setHasSecondStroke(final boolean hasSecondStroke) {
    myHasSecondStroke = hasSecondStroke;
  }

  public boolean isHasSecondStroke() {
    return myHasSecondStroke;
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  public void setDataContext(final DataContext dataContext) {
    myDataContext = dataContext;
  }

  public boolean isModalContext() {
    return isModalContext;
  }

  public void setModalContext(final boolean modalContext) {
    isModalContext = modalContext;
  }

  public Component getFocusOwner() {
    return myFocusOwner;
  }

  public void setFocusOwner(final Component focusOwner) {
    myFocusOwner = focusOwner;
  }

  public void setInputEvent(final KeyEvent e) {
    myInputEvent = e;
  }

  public KeyEvent getInputEvent() {
    return myInputEvent;
  }
}