// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

//TODO actually it doesn't look like a good idea because all there methods will be added to mainly all PsiElements
public interface JvmAnnotationTreeElement {

  @Nullable
  default JvmAnnotationTreeElement getParentInAnnotation() {
    return null;
  }


  @NotNull
  default List<JvmAnnotationTreeElement> getAnnotationChildren() {
    return Collections.emptyList();
  }

  @Nullable
  static <T extends JvmAnnotationTreeElement> T getParentOfType(@Nullable JvmAnnotationTreeElement element, Class<T> tClass) {
    while (element != null) {
      if (tClass.isAssignableFrom(element.getClass())) {
        return (T)element;
      }
      element = element.getParentInAnnotation();
    }
    return null;
  }
}
