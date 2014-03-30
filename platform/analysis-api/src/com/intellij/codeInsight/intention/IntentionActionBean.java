/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.intention;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.ResourceBundle;

public class IntentionActionBean extends CustomLoadingExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.IntentionActionBean");
  @Tag("className")
  public String className;
  @Tag("category")
  public String category;
  @Tag("categoryKey")
  public String categoryKey;
  @Tag("bundleName")
  public String bundleName;
  @Tag("descriptionDirectoryName")
  public String descriptionDirectoryName;

  @Nullable
  public String[] getCategories() {
    if (categoryKey != null) {
      final String baseName = bundleName != null ? bundleName : ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName();
      if (baseName == null) {
        LOG.error("No resource bundle specified for "+myPluginDescriptor);
      }
      final ResourceBundle bundle = AbstractBundle.getResourceBundle(baseName, myPluginDescriptor.getPluginClassLoader());

      final String[] keys = categoryKey.split("/");
      if (keys.length > 1) {
        return ContainerUtil.map2Array(keys, String.class, new Function<String, String>() {
          @Override
          public String fun(final String s) {
            return CommonBundle.message(bundle, s);
          }
        });
      }

      category = CommonBundle.message(bundle, categoryKey);
    }
    if (category == null) return null;
    return category.split("/");
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }

  public IntentionAction instantiate() throws ClassNotFoundException {
    return (IntentionAction)instantiateExtension(className, ApplicationManager.getApplication().getPicoContainer());
  }

  public ClassLoader getMetadataClassLoader() {
    return myPluginDescriptor.getPluginClassLoader();
  }
}
