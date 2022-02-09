// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

@SuppressWarnings("ClassExplicitlyAnnotation")
final class StorageImpl implements Storage {
  private final String myValue;
  private final String myFile;
  private final boolean myDeprecated;
  private final boolean myExclusive;
  private final boolean myExportable;
  private final RoamingType myType;
  private final Class<? extends StateSplitter> mySplitter;
  private final Class<? extends StateStorage> myClass;
  private final ThreeState myUseSaveThreshold;

  StorageImpl(String value,
              String file,
              boolean deprecated,
              boolean exclusive,
              boolean exportable,
              RoamingType roamingType,
              Class<? extends StateSplitter> stateSplitter,
              Class<? extends StateStorage> storageClass,
              ThreeState useSaveThreshold) {
    myValue = value;
    myFile = file;
    myDeprecated = deprecated;
    myExclusive = exclusive;
    myExportable = exportable;
    myType = roamingType;
    mySplitter = stateSplitter;
    myClass = storageClass;
    myUseSaveThreshold = useSaveThreshold;
  }

  @NotNull
  static Storage copyWithNewValue(@NotNull Storage original, @NotNull String newValue) {
    return new StorageImpl(newValue, newValue, original.deprecated(), original.exclusive(), original.exportable(), original.roamingType(),
                           original.stateSplitter(), original.storageClass(), original.useSaveThreshold());
  }

  @NotNull
  static Storage deprecatedCopy(@NotNull Storage original) {
    //noinspection deprecation
    return new StorageImpl(original.value(), original.file(), true, original.exclusive(), original.exportable(),
                           original.roamingType(), original.stateSplitter(), original.storageClass(), original.useSaveThreshold());
  }

  @Override
  public String file() {
    return myFile;
  }

  @Override
  public String value() {
    return myValue;
  }

  @Override
  public boolean deprecated() {
    return myDeprecated;
  }

  @Override
  public RoamingType roamingType() {
    return myType;
  }

  @Override
  public Class<? extends StateStorage> storageClass() {
    return myClass;
  }

  @Override
  public Class<? extends StateSplitter> stateSplitter() {
    return mySplitter;
  }

  @Override
  public ThreeState useSaveThreshold() {
    return myUseSaveThreshold;
  }

  @Override
  public boolean exclusive() {
    return myExclusive;
  }

  @Override
  public boolean exportable() {
    return myExportable;
  }

  @Override
  public boolean usePathMacroManager() {
    return true;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
  }
}
