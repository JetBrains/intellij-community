package com.intellij.configurationStore;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
final class ProjectFileStorageAnnotation implements Storage {
  private String path;

  private boolean deprecated;

  ProjectFileStorageAnnotation(String path, boolean deprecated) {
    this.path = path;
    this.deprecated = deprecated;
  }

  @Override
  public String id() {
    return "default";
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
  public StorageScheme scheme() {
    return StorageScheme.DEFAULT;
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
  public Class<StateSplitterEx> stateSplitter() {
    return StateSplitterEx.class;
  }

  @NotNull
  @Override
  public Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}