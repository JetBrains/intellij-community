/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

/**
 * @author Mike
 */
public interface XmlElementContentSpec extends XmlElement {
  public boolean isEmpty();
  public boolean isAny();
  public boolean isMixed();
  public boolean hasChildren();
}
