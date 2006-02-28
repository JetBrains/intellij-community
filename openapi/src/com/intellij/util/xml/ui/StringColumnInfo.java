/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementPresentation;

/**
 * @author peter
 */
public class StringColumnInfo<T extends DomElement> extends StripeColumnInfo<T, String> {
  public StringColumnInfo(final String name) {
    super(name);
  }

  public String valueOf(final T object) {
    return object.getPresentation().getTypeName();
  }
}
