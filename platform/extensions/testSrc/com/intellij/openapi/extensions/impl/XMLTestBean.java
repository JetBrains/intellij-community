// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Alexander Kireyev
 */
public class XMLTestBean implements PluginAware {
  private boolean otherProperty;
  private int prop1;
  private Object prop2;
  private Collection<String> collectionProperty = new ArrayList<>();
  private PluginId pluginId;

  public XMLTestBean() {
  }

  public XMLTestBean(Collection aCollectionProperty, boolean aOtherProperty, int aProp1) {
    collectionProperty = aCollectionProperty;
    otherProperty = aOtherProperty;
    prop1 = aProp1;
  }

  public boolean isOtherProperty() {
    return otherProperty;
  }

  public void setOtherProperty(boolean otherProperty) {
    this.otherProperty = otherProperty;
  }

  @Tag("prop1")
  public int getProp1() {
    return prop1;
  }

  public void setProp1(int prop1) {
    this.prop1 = prop1;
  }

  public Object getProp2() {
    return prop2;
  }

  public void setProp2(Object prop2) {
    this.prop2 = prop2;
  }

  public Collection<String> getCollectionProperty() {
    return collectionProperty;
  }

  public void setCollectionProperty(Collection<String> collectionProperty) {
    this.collectionProperty = collectionProperty;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    pluginId = pluginDescriptor.getPluginId();
  }

  public PluginId getPluginId() {
    return pluginId;
  }
}
