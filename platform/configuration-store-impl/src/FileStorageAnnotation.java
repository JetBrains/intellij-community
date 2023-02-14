// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class FileStorageAnnotation implements Storage {
  protected final String path;

  private final boolean deprecated;
  private final Class<? extends StateSplitterEx> mySplitterClass;

  public FileStorageAnnotation(@NotNull String path, boolean deprecated) {
    this(path, deprecated, StateSplitterEx.class);
  }

  public FileStorageAnnotation(@NotNull String path, boolean deprecated, Class<? extends StateSplitterEx> splitterClass) {
    this.path = path;
    this.deprecated = deprecated;
    mySplitterClass = splitterClass;
  }

  @Override
  public ThreeState useSaveThreshold() {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean exclusive() {
    return false;
  }

  @Override
  public boolean exportable() {
    return false;
  }

  @Override
  public boolean usePathMacroManager() {
    return true;
  }

  @Override
  public String file() {
    return value();
  }

  @Override
  public String value() {
    return path;
  }

  @Override
  public boolean deprecated() {
    return deprecated;
  }

  @Override
  public RoamingType roamingType() {
    return RoamingType.DEFAULT;
  }

  @Override
  public Class<? extends StateStorage> storageClass() {
    return StateStorage.class;
  }

  @Override
  public Class<? extends StateSplitterEx> stateSplitter() {
    return mySplitterClass;
  }

  @NotNull
  @Override
  public Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}