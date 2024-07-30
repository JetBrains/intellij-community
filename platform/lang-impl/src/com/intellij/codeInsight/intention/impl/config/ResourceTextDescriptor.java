// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.l10n.LocalizationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

final class ResourceTextDescriptor implements TextDescriptor {
  private static final Logger LOG = Logger.getInstance(ResourceTextDescriptor.class);
  private final ClassLoader loader;
  private final String resourcePath;

  ResourceTextDescriptor(ClassLoader loader, @NotNull String resourcePath) {
    this.loader = loader;
    this.resourcePath = resourcePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceTextDescriptor resource = (ResourceTextDescriptor)o;
    return Objects.equals(loader, resource.loader) &&
           Objects.equals(resourcePath, resource.resourcePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(loader, resourcePath);
  }

  @Override
  public @NotNull String getText() throws IOException {
    InputStream inputStream = LocalizationUtil.INSTANCE.getResourceAsStream(loader, resourcePath);
    if (inputStream != null) {
      try (inputStream) {
        return ResourceUtil.loadText(inputStream); //NON-NLS
      }
      catch (IOException e) {
        LOG.error("Cannot find localized resource: " + resourcePath, e);
      }
    }
    InputStream stream = loader.getResourceAsStream(resourcePath);
    if (stream == null) {
      throw new IOException("Resource not found: " + resourcePath + "; loader: " + loader);
    }
    return ResourceUtil.loadText(stream); //NON-NLS
  }

  @Override
  public @NotNull String getFileName() {
    return Strings.trimEnd(resourcePath.substring(resourcePath.lastIndexOf('/') + 1), BeforeAfterActionMetaData.EXAMPLE_USAGE_URL_SUFFIX);
  }
}