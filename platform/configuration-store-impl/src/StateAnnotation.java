package com.intellij.configurationStore;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
public class StateAnnotation implements State {
  private String name;
  @NotNull
  private final Storage[] storages;

  public StateAnnotation(@NotNull String name, @NotNull Storage storage) {
    this.name = name;
    storages = new Storage[]{storage};
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Storage[] storages() {
    return storages;
  }

  @Override
  public boolean reloadable() {
    return false;
  }

  @Override
  public boolean defaultStateAsResource() {
    return false;
  }

  @Override
  public String additionalExportFile() {
    return null;
  }

  @Override
  public Class<? extends NameGetter> presentableName() {
    return null;
  }

  @Override
  public boolean externalStorageOnly() {
    return false;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}
