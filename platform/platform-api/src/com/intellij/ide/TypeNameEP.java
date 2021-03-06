// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;

import java.util.ResourceBundle;

/**
 * Provides type name for instances of given class.
 * <p/>
 * Register via {@code com.intellij.typeName} extension point.
 *
 * @author yole
 */
public class TypeNameEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TypeNameEP> EP_NAME = ExtensionPointName.create("com.intellij.typeName");

  @Attribute("className")
  @RequiredElement
  public String className;

  /**
   * Use {@link #resourceKey} for i18n.
   */
  @Attribute("name")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String name;

  /**
   * If unspecified, plugin's {@code <resource-bundle>} will be used.
   */
  @Attribute("resourceBundle")
  public String resourceBundle;

  @Attribute("resourceKey")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String resourceKey;

  private final NullableLazyValue<String> myName = new NullableLazyValue<>() {
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
          ResourceBundle bundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, getLoaderForClass());
          return BundleBase.message(bundle, resourceKey);
        }
      }
      return null;
    }
  };

  public NullableLazyValue<String> getTypeName() {
    return myName;
  }
}
