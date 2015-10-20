package com.intellij.configurationStore;

import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;

/**
 * This class was introduced as a workaround for Kotlin bug (https://youtrack.jetbrains.com/issue/KT-9670) to avoid creation of
 * unnecessary instances of KClass
 * todo[nik] remove when KT-9670 is fixed
 */
public class JavaAnnotationHelperForKotlin {
  public static Class<? extends StateStorage> getStorageClass(Storage storage) {
    return storage.storageClass();
  }

  public static Class<? extends StateSplitter> getStateSplitterClass(Storage storage) {
    return storage.stateSplitter();
  }
}
