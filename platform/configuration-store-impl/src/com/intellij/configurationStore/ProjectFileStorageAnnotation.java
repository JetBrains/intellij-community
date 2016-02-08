package com.intellij.configurationStore;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class ProjectFileStorageAnnotation implements Storage {
  @Override
  public String file() {
    return value();
  }

  @Override
  public String value() {
    return ProjectStoreImplKt.PROJECT_FILE;
  }

  @Override
  public StorageScheme scheme() {
    return StorageScheme.DEFAULT;
  }

  @Override
  public boolean deprecated() {
    return false;
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