// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.impl.cache.ExplicitTypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.TypePath;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

class ClsTypeAnnotationCollector extends ExplicitTypeAnnotationContainer.Collector {
  private final @NotNull FirstPassData myFirstPassData;

  ClsTypeAnnotationCollector(@NotNull TypeInfo info, @NotNull FirstPassData classInfo) {
    super(info);
    myFirstPassData = classInfo;
  }

  void add(TypePath path, String text) {
    byte[] translated = translatePath(path);
    if (translated != null) {
      add(translated, text);
    }
  }

  AnnotationVisitor collect(@Nullable TypePath path, @Nullable String desc) {
    return new AnnotationTextCollector(desc, myFirstPassData, text -> add(path, text));
  }

  /**
   * Translate annotation path. The most non-trivial thing here is converting inner-to-outer traversal
   * into outer-to-inner. E.g. consider {@code @A Outer.@B Inner} (assuming that Inner is non-static).
   * Class-file stores empty path for {@code @A} and INNER_TYPE path for {@code @B}. We need the reverse,
   * as when we build the PSI we don't know how many non-static components we have. So we translate path
   * for {@code @A} to ENCLOSING_CLASS and path for {@code @B} to empty.
   *
   * @param path TypePath
   * @return translated path in the form of byte array
   */
  private byte[] translatePath(@Nullable TypePath path) {
    TypeInfo curType = myTypeInfo;
    int depth = curType.innerDepth(myFirstPassData);
    if (path == null) {
      if (depth == 0) {
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
      byte[] result = new byte[depth];
      Arrays.fill(result, ENCLOSING_CLASS);
      return result;
    }
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    int length = path.getLength();
    for (int i = 0; i < length; i++) {
      byte step = (byte)path.getStep(i);
      if (step == TypePath.INNER_TYPE) {
        if (depth == 0) return null;
        depth--;
        continue;
      }
      while (depth-- > 0) {
        result.write(ENCLOSING_CLASS);
        if (!(curType instanceof TypeInfo.RefTypeInfo)) return null;
        curType = ((TypeInfo.RefTypeInfo)curType).outerType();
      }
      switch (step) {
        case TypePath.ARRAY_ELEMENT:
          if (!(curType instanceof TypeInfo.DerivedTypeInfo) ||
              (curType.getKind() != TypeInfo.TypeKind.ARRAY && curType.getKind() != TypeInfo.TypeKind.ELLIPSIS)) {
            return null;
          }
          curType = ((TypeInfo.DerivedTypeInfo)curType).child();
          result.write(ARRAY_ELEMENT);
          break;
        case TypePath.WILDCARD_BOUND:
          if (!(curType instanceof TypeInfo.DerivedTypeInfo) ||
              (curType.getKind() != TypeInfo.TypeKind.EXTENDS && curType.getKind() != TypeInfo.TypeKind.SUPER)) {
            return null;
          }
          curType = ((TypeInfo.DerivedTypeInfo)curType).child();
          result.write(WILDCARD_BOUND);
          break;
        case TypePath.TYPE_ARGUMENT:
          int argumentIndex = path.getStepArgument(i);
          if (!(curType instanceof TypeInfo.RefTypeInfo)) return null;
          curType = ((TypeInfo.RefTypeInfo)curType).genericComponent(argumentIndex);
          result.write(TYPE_ARGUMENT);
          result.write(argumentIndex);
          break;
        default:
          return null;
      }
      if (curType == null) return null;
      depth = curType.innerDepth(myFirstPassData);
    }
    while (depth-- > 0) {
      result.write(ENCLOSING_CLASS);
    }
    return result.toByteArray();
  }
}
