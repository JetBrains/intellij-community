/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.net.URL;

import static com.intellij.DynamicBundle.findLanguageBundle;

/**
 * @author Eugene Zhuravlev
 */
public class DefaultTemplate {
  private static final Logger LOG = Logger.getInstance(DefaultTemplate.class);
  
  private final String myName;
  private final String myExtension;

  private final URL myTextURL;
  private final String myDescriptionPath;
  private Reference<String> myText;
  
  @Nullable
  private final URL myDescriptionURL;
  private Reference<String> myDescriptionText;

  public DefaultTemplate(@NotNull String name, @NotNull String extension, @NotNull URL templateURL, @Nullable URL descriptionURL) {
    this(name, extension, templateURL, descriptionURL, null);
  }

  DefaultTemplate(@NotNull String name, @NotNull String extension, @NotNull URL templateURL, @Nullable URL descriptionURL, @Nullable String descriptionPath) {
    myName = name;
    myExtension = extension;
    myTextURL = templateURL;
    myDescriptionURL = descriptionURL;
    myDescriptionPath = descriptionPath;
  }

  @NotNull
  private static @NlsSafe String loadText(@NotNull ThrowableComputable<String, IOException> computable) {
    String text = "";
    try {
      text = StringUtil.convertLineSeparators(computable.compute());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getQualifiedName() {
    return FileTemplateBase.getQualifiedName(getName(), getExtension());
  }

  @NotNull
  public String getExtension() {
    return myExtension;
  }

  @NotNull
  public URL getTemplateURL() {
    return myTextURL;
  }

  @NotNull
  public String getText() {
    String text = SoftReference.dereference(myText);
    if (text == null) {
      text = loadText(() -> UrlUtil.loadText(myTextURL));
      myText = new java.lang.ref.SoftReference<>(text);
    }
    return text;
  }

  @NotNull
  public @Nls String getDescriptionText() {
    if (myDescriptionURL == null) return "";
    String text = SoftReference.dereference(myDescriptionText); //NON-NLS
    if (text == null) {
      text = loadText(() -> {
        DynamicBundle.LanguageBundleEP langBundle = findLanguageBundle();
        PluginDescriptor descriptor = langBundle != null ? langBundle.pluginDescriptor : null;
        ClassLoader langBundleLoader = descriptor != null ? descriptor.getPluginClassLoader() : null;
        if (langBundleLoader != null && myDescriptionPath != null) {
          InputStream stream = langBundleLoader.getResourceAsStream(FileTemplatesLoader.TEMPLATES_DIR + "/" + myDescriptionPath);
          if (stream != null) {
            return ResourceUtil.loadText(stream);
          }
        }
        return UrlUtil.loadText(myDescriptionURL);
      });
      myDescriptionText = new java.lang.ref.SoftReference<>(text);
    }
    return text;
  }
}
