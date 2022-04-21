// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Supplier;

import static com.intellij.DynamicBundle.findLanguageBundle;

/**
 * @author Eugene Zhuravlev
 */
public final class DefaultTemplate {
  private static final Logger LOG = Logger.getInstance(DefaultTemplate.class);
  
  private final String myName;
  private final String myExtension;

  private final Supplier<String> textSupplier;
  private final String myDescriptionPath;
  private Reference<String> myText;
  
  private final @Nullable Supplier<String> descriptionSupplier;
  private Reference<String> myDescriptionText;

  /**
   * @deprecated Use {@link #DefaultTemplate(String, String, Supplier, Supplier, String)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public DefaultTemplate(@NotNull String name, @NotNull String extension, @NotNull URL templateUrl, @Nullable URL descriptionUrl) {
    this(name, extension, () -> {
      try {
        return ResourceUtil.loadText(templateUrl.openStream());
      }
      catch (IOException e) {
        LOG.error(e);
        return "";
      }
    }, descriptionUrl == null ? null : () -> {
      try {
        return ResourceUtil.loadText(descriptionUrl.openStream());
      }
      catch (IOException e) {
        LOG.error(e);
        return "";
      }
    }, null);
  }

  DefaultTemplate(@NotNull String name,
                  @NotNull String extension,
                  @NotNull Supplier<String> textSupplier,
                  @Nullable Supplier<String> descriptionSupplier,
                  @Nullable String descriptionPath) {
    myName = name;
    myExtension = extension;
    this.textSupplier = textSupplier;
    this.descriptionSupplier = descriptionSupplier;
    myDescriptionPath = descriptionPath;
  }

  private static @NotNull @NlsSafe String loadText(@NotNull ThrowableComputable<String, IOException> computable) {
    String text = "";
    try {
      text = StringUtil.convertLineSeparators(computable.compute());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  public @NotNull String getName() {
    return myName;
  }

  public @NotNull String getQualifiedName() {
    return FileTemplateBase.getQualifiedName(getName(), getExtension());
  }

  public @NotNull String getExtension() {
    return myExtension;
  }

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  public URL getTemplateURL() {
    // the only external usage - https://github.com/wrdv/testme-idea/blob/8e314aea969619f43f0c6bb17f53f1d95b1072be/src/main/java/com/weirddev/testme/intellij/ui/template/FTManager.java#L200
    try {
      return new URL("https://not.relevant");
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public @NotNull String getText() {
    String text = SoftReference.dereference(myText);
    if (text == null) {
      text = textSupplier.get();
      myText = new java.lang.ref.SoftReference<>(text);
    }
    return text;
  }

  public @NotNull @Nls String getDescriptionText() {
    if (descriptionSupplier == null) return "";
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
        return descriptionSupplier.get();
      });
      myDescriptionText = new java.lang.ref.SoftReference<>(text);
    }
    return text;
  }

  @Override
  public String toString() {
    return textSupplier.toString();
  }
}
