/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.xml.events.*;

/**
 * @author peter
 */
public interface DomEventVisitor {
  void visitValueChangeEvent(final TagValueChangeEvent event);

  void visitElementDefined(final ElementDefinedEvent event);

  void visitElementUndefined(final ElementUndefinedEvent event);

  void visitElementChangedEvent(final ElementChangedEvent event);

  void visitCollectionElementAddedEvent(final CollectionElementAddedEvent event);

  void visitCollectionElementRemovedEvent(final CollectionElementRemovedEvent event);
}
