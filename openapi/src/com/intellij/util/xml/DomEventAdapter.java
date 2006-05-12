/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.xml.events.*;

/**
 * @author peter
 */
public abstract class DomEventAdapter implements DomEventVisitor, DomEventListener {

  public void valueChanged(TagValueChangeEvent event) {
  }

  public void elementDefined(ElementDefinedEvent event) {
  }

  public void elementUndefined(ElementUndefinedEvent event) {
  }

  public void elementChanged(ElementChangedEvent event) {
  }

  public void childAdded(CollectionElementAddedEvent event) {
  }

  public void childRemoved(CollectionElementRemovedEvent event) {
  }

  public void eventOccured(DomEvent event) {
    event.accept(this);
  }

  public final void visitValueChangeEvent(final TagValueChangeEvent event) {
    valueChanged(event);
  }

  public final void visitElementDefined(final ElementDefinedEvent event) {
    elementDefined(event);
  }

  public final void visitElementUndefined(final ElementUndefinedEvent event) {
    elementUndefined(event);
  }

  public final void visitElementChangedEvent(final ElementChangedEvent event) {
    elementChanged(event);
  }

  public final void visitCollectionElementAddedEvent(final CollectionElementAddedEvent event) {
    childAdded(event);
  }

  public final void visitCollectionElementRemovedEvent(final CollectionElementRemovedEvent event) {
    childRemoved(event);
  }
}
