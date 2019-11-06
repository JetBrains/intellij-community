// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.AbstractBundle;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;

import java.util.ResourceBundle;

/**
 * @author yole
 */
public class TypeNameEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TypeNameEP> EP_NAME = ExtensionPointName.create("com.intellij.typeName");

  @Attribute("className")
  public String className;

  @Attribute("name")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String name;

  @Attribute("resourceBundle")
  public String resourceBundle;

  @Attribute("resourceKey")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String resourceKey;

  private final NullableLazyValue<String> myName = new NullableLazyValue<String>() {
    @Override
    protected String compute() {
      if (name != null) {
        return name;
      }
      if (resourceKey != null) {
        String bundleName = resourceBundle;
        if (bundleName == null && myPluginDescriptor != null) {
          bundleName = myPluginDescriptor.getResourceBundleBaseName();
        }
        if (bundleName != null) {
          ResourceBundle bundle = AbstractBundle.getResourceBundle(bundleName, getLoaderForClass());
          return bundle.getString(resourceKey);
        }
      }
      return null;
    }
  };

  public NullableLazyValue<String> getTypeName() {
    return myName;
  }
}
