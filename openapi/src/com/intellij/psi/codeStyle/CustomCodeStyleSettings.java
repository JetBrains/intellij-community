/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CustomCodeStyleSettings implements Cloneable {
  private final CodeStyleSettings myContainer;
  private final String myTagName;

  protected CustomCodeStyleSettings(@NonNls @NotNull String tagName, CodeStyleSettings container) {
    myTagName = tagName;
    myContainer = container;
  }

  public final CodeStyleSettings getContainer() {
    return myContainer;
  }

  @NonNls @NotNull
  public final String getTagName() {
    return myTagName;
  }

  public void readExternal(Element parentElement) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentElement.getChild(myTagName));
  }

  public void writeExternal(Element parentElement, @NotNull final CustomCodeStyleSettings parentSettings) throws WriteExternalException {
    final Element childElement = new Element(myTagName);
    DefaultJDOMExternalizer.writeExternal(this, childElement, new DifferenceFilter<CustomCodeStyleSettings>(this, parentSettings));
    if (!childElement.getContent().isEmpty()) {
      parentElement.addContent(childElement);
    }
  }

  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

}
