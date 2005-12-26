/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.events;

import com.intellij.util.xml.events.ElementChangedEvent;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomEventVisitor;

/**
 * @author peter
 */
public class ElementUndefinedEvent extends ElementChangedEvent {

  public ElementUndefinedEvent(final DomElement element) {
    super(element);
  }

  public String toString() {
    return "Undefined " + getElement();
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementUndefined(this);
  }
}
