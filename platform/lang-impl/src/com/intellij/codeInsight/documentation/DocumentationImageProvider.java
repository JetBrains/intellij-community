// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import java.awt.*;
import java.awt.image.renderable.RenderableImageProducer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Objects;

final class DocumentationImageProvider extends Dictionary<URL, Image> {

  private final @NotNull Component myReferenceComponent;
  private final @NotNull DocumentationImageResolver myImageResolver;

  DocumentationImageProvider(
    @NotNull Component referenceComponent,
    @NotNull DocumentationImageResolver imageResolver
  ) {
    myReferenceComponent = referenceComponent;
    myImageResolver = imageResolver;
  }

  @Override
  public Image get(Object key) {
    if (key == null) {
      return null;
    }
    else {
      return getImage((URL)key);
    }
  }

  private @Nullable Image getImage(@NotNull URL url) {
    Image inMemory = myImageResolver.resolveImage(url.toExternalForm());
    if (inMemory != null) {
      return inMemory;
    }
    return Toolkit.getDefaultToolkit().createImage(
      new RenderableImageProducer(
        new DocumentationRenderableImage(
          Objects.requireNonNullElse(builtinServerUrl(url), url),
          myReferenceComponent
        ),
        null
      )
    );
  }

  private static @Nullable URL builtinServerUrl(@NotNull URL url) {
    Url parsedUrl = Urls.parseEncoded(url.toExternalForm());
    if (parsedUrl == null) {
      return null;
    }
    BuiltInServerManager builtInServerManager = BuiltInServerManager.getInstance();
    if (!builtInServerManager.isOnBuiltInWebServer(parsedUrl)) {
      return null;
    }
    try {
      return new URL(builtInServerManager.addAuthToken(parsedUrl).toExternalForm());
    }
    catch (MalformedURLException e) {
      DocumentationManager.LOG.warn(e);
      return null;
    }
  }

  @Override
  public int size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Enumeration<URL> keys() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Enumeration<Image> elements() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image put(URL key, Image value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image remove(Object key) {
    throw new UnsupportedOperationException();
  }
}
