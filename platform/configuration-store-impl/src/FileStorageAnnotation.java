package com.intellij.configurationStore;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public final class FileStorageAnnotation implements Storage {
  private String path;

  private boolean deprecated;
  private final Class<? extends StateStorage> storageClass;

  public FileStorageAnnotation(@NotNull String path, boolean deprecated) {
    this(path, deprecated, StateStorage.class);
  }

  public FileStorageAnnotation(@NotNull String path, boolean deprecated, @Nullable Class<? extends StateStorage> storageClass) {
    this.path = path;
    this.deprecated = deprecated;
    this.storageClass = storageClass;
  }

  @Override
  public String id() {
    return "default";
  }

  @Override
  public boolean exclusive() {
    return false;
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
    return storageClass;
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