/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

import org.jdom.Element;

/**
 * @author Alexander Kireyev
 */
public class SortingException extends RuntimeException {
  private Element[] myConflictingElements;

  public SortingException(String message, Element[] conflictingElements) {
    super(message);
    myConflictingElements = conflictingElements;
  }

  public Element[] getConflictingElements() {
    return myConflictingElements;
  }
}
