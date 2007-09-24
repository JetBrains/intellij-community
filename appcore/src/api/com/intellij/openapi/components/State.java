package com.intellij.openapi.components;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface State {
  String name();
  Storage[] storages();
  Class<? extends StateStorageChooser> storageChooser() default StorageAnnotationsDefaultValues.NullStateStorageChooser.class;
  boolean reloadable() default true;
}
