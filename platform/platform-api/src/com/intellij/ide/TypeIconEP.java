// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;

import javax.swing.*;

/**
 * @author yole
 */
public class TypeIconEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TypeIconEP> EP_NAME = ExtensionPointName.create("com.intellij.typeIcon");

  @Attribute("className")
  @RequiredElement
  public String className;

  @Attribute("icon")
  @RequiredElement
  public String icon;

  private final NullableLazyValue<Icon> myIcon = new NullableLazyValue<Icon>() {
    @Override
    protected Icon compute() {
      return IconLoader.findIcon(icon, getLoaderForClass());
    }
  };

  public NullableLazyValue<Icon> getIcon() {
    return myIcon;
  }
}
