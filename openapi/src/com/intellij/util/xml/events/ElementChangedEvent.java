/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.events;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventVisitor;

/**
 * @author peter
 */
public class ElementChangedEvent implements DomEvent {
  private final DomElement myElement;

  public ElementChangedEvent(final DomElement element) {
    assert element != null;
    myElement = element;
  }

  public final DomElement getElement() {
    return myElement;
  }

  public String toString() {
    return "Changed " + myElement;
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementChangedEvent(this);
  }
}
