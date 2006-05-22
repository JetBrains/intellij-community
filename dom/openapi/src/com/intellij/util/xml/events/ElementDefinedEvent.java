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
public class ElementDefinedEvent extends ElementChangedEvent {
  public ElementDefinedEvent(final DomElement element) {
    super(element);
  }

  public String toString() {
    return "Defined " + getElement();
  }

  public void accept(DomEventVisitor visitor) {
    visitor.visitElementDefined(this);
  }
}
