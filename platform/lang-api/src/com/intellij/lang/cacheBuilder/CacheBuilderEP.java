// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.cacheBuilder;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CacheBuilderEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance(CacheBuilderEP.class);

  private CacheBuilderEP() {
  }

  @Attribute("fileType")
  public String fileType;
  @Attribute("wordsScannerClass")
  public String wordsScannerClass;

  private transient Class<WordsScanner> cachedClass;
  private transient PluginDescriptor pluginDescriptor;

  @ApiStatus.Internal
  public String getFileType() {
    return fileType;
  }

  @ApiStatus.Internal
  public WordsScanner getWordsScanner() {
    try {
      Class<WordsScanner> aClass = cachedClass;
      Application app = ApplicationManager.getApplication();
      if (aClass == null) {
        aClass = app.loadClass(wordsScannerClass, pluginDescriptor);
        cachedClass = aClass;
      }
      return app.instantiateClass(aClass, pluginDescriptor.getPluginId());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (PluginException e) {
      LOG.error(e);
      return null;
    }
    catch (Exception e) {
      LOG.error(new PluginException(e, pluginDescriptor.getPluginId()));
      return null;
    }
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}