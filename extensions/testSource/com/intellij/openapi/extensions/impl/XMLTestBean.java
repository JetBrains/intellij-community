/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;

import java.util.Collection;

/**
 * @author Alexander Kireyev
 */
public class XMLTestBean implements PluginAware {
  private boolean myOtherProperty;
  private int myProp1;
  private Object myProp2;
  private Collection myCollectionProperty;
  private String myPluginName;

  public XMLTestBean() {
  }

  public XMLTestBean(Collection aCollectionProperty, boolean aOtherProperty, int aProp1) {
    myCollectionProperty = aCollectionProperty;
    myOtherProperty = aOtherProperty;
    myProp1 = aProp1;
  }

  public boolean isOtherProperty() {
    return myOtherProperty;
  }

  public void setOtherProperty(boolean otherProperty) {
    myOtherProperty = otherProperty;
  }

  public int getProp1() {
    return myProp1;
  }

  public void setProp1(int prop1) {
    myProp1 = prop1;
  }

  public Object getProp2() {
    return myProp2;
  }

  public void setProp2(Object prop2) {
    myProp2 = prop2;
  }

  public Collection getCollectionProperty() {
    return myCollectionProperty;
  }

  public void setCollectionProperty(Collection collectionProperty) {
    myCollectionProperty = collectionProperty;
  }

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginName = pluginDescriptor.getPluginName();
  }

  public String getPluginName() {
    return myPluginName;
  }
}
