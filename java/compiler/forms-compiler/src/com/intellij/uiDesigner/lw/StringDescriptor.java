// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import java.util.Objects;

public final class StringDescriptor {
  /**
   * Name of resource bundle
   */
  private final String myBundleName;
  /**
   * Key in the resource bundle
   */
  private final String myKey;
  /**
   * Value has sense if it's calculated not via resource bundle
   */
  private final String myValue;
  /**
   * Cached resolved value. We need it here to speed up property inspector
   * painting.
   */
  private String myResolvedValue;

  /**
   * Marker for string values which do not need internationalization
   */
  private boolean myNoI18n;
  
  private String myFormClass;

  private StringDescriptor(final String value) {
    if (value == null) {
      throw new IllegalArgumentException("value cannot be null");
    }
    myBundleName = null;
    myKey = null;
    myValue = value;
  }

  public StringDescriptor(final String bundleName, final String key) {
    if (bundleName == null) {
      throw new IllegalArgumentException("bundleName cannot be null");
    }
    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }
    myBundleName = bundleName.replace('.', '/');
    myKey = key;
    myValue = null;
  }

  /**
   * Creates "trivial" StringDescriptor.
   */
  public static StringDescriptor create(final String value){
    return value != null ? new StringDescriptor(value) : null;
  }

  /**
   * @return not {@code null} value if this is "trivial" StringDescriptor.
   * If StringDescriptor is "trivial" then {@link #getBundleName()} and {@link #getKey()}
   * return {@code null}.
   */
  public String getValue(){
    return myValue;
  }

  /**
   * @return not {@code null} value if this is non "trivial" StringDescriptor.
   */
  public String getBundleName() {
    return myBundleName;
  }

  public String getDottedBundleName() {
    return myBundleName == null ? null : myBundleName.replace('/', '.');
  }

  /**
   * @return not {@code null} value if this is non "trivial" StringDescriptor.
   */
  public String getKey() {
    return myKey;
  }

  /**
   * @return can be null
   */
  public String getResolvedValue() {
    return myResolvedValue;
  }

  /**
   * @param resolvedValue can be null
   */
  public void setResolvedValue(final String resolvedValue) {
    myResolvedValue = resolvedValue;
  }

  public boolean isNoI18n() {
    return myNoI18n;
  }

  public void setNoI18n(final boolean noI18n) {
    myNoI18n = noI18n;
  }

  public String getFormClass() {
    return myFormClass;
  }

  public void setFormClass(String formClass) {
    myFormClass = formClass;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof StringDescriptor)) return false;

    final StringDescriptor descriptor = (StringDescriptor)o;

    if (!Objects.equals(myBundleName, descriptor.myBundleName)) return false;
    if (!Objects.equals(myKey, descriptor.myKey)) return false;
    if (!Objects.equals(myValue, descriptor.myValue)) return false;
    if (myNoI18n != descriptor.myNoI18n) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myBundleName != null ? myBundleName.hashCode() : 0);
    result = 29 * result + (myKey != null ? myKey.hashCode() : 0);
    result = 29 * result + (myValue != null ? myValue.hashCode() : 0);
    return result;
  }

  public String toString() {
    if (myValue != null) {
      return "[StringDescriptor:" + myValue + "]";
    }
    return "[StringDescriptor" + myBundleName + ":" + myKey + "]";
  }
}
