package com.intellij.openapi.components;

public @interface Storage {
  String id();
  String file() default "";
  Class<? extends StateStorage> storageClass() default StorageAnnotationsDefaultValues.NullStateStorage.class;
}
