package com.intellij.configurationStore;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public final class DefaultStorageAnnotation implements Storage {
  @Override
  public String id() {
    return "___Default___";
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public String file() {
    return StoragePathMacros.PROJECT_FILE;
  }

  @Override
  public StorageScheme scheme() {
    return StorageScheme.DEFAULT;
  }

  @Override
  public boolean deprecated() {
    return true;
  }

  @Override
  public RoamingType roamingType() {
    return RoamingType.PER_USER;
  }

  @Override
  public Class<? extends StateStorage> storageClass() {
    return StateStorage.class;
  }

  @Override
  public Class<StateSplitterEx> stateSplitter() {
    return StateSplitterEx.class;
  }

  @NotNull
  @Override
  public Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}