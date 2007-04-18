package com.intellij.openapi.components;

public @interface Storage {
  String id();
  String file() default "";
  StorageScheme scheme() default StorageScheme.DEFAULT;

  Class<? extends StateStorage> storageClass() default StorageAnnotationsDefaultValues.NullStateStorage.class;
  Class<? extends StateSplitter> stateSplitter() default StorageAnnotationsDefaultValues.NullStateSplitter.class;
}
