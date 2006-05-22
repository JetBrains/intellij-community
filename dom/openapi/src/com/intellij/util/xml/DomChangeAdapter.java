/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.xml.events.*;

/**
 * @author peter
 */
public abstract class DomChangeAdapter extends DomEventAdapter {

  protected abstract void elementChanged(DomElement element);

  public final void childAdded(CollectionElementAddedEvent event) {
    elementChanged(event.getParent());
  }

  public final void childRemoved(CollectionElementRemovedEvent event) {
    elementChanged(event.getParent());
  }

  public final void elementChanged(ElementChangedEvent event) {
    elementChanged(event.getElement());
  }

  public final void elementDefined(ElementDefinedEvent event) {
    elementChanged(event.getElement());
  }

  public final void elementUndefined(ElementUndefinedEvent event) {
    elementChanged(event.getElement());
  }

  public final void valueChanged(TagValueChangeEvent event) {
    elementChanged(event.getElement());
  }
}
