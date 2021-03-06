// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.TypePath;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

class ClsTypeAnnotationCollector extends TypeAnnotationContainer.Collector {
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
   * Class-file stores empty path for {@code @A} and INNER_TYPE path for {@code @A}. We need the reverse,
   * as when we build the PSI we don't know how many non-static components we have. So we translate path
   * for {@code @A} to ENCLOSING_CLASS and path for {@code @B} to empty.
   *
   * @param path TypePath
   * @return translated path in the form of byte array
   */
  private byte[] translatePath(@Nullable TypePath path) {
    String typeText = myTypeInfo.text;
    int arrayLevel = myTypeInfo.arrayCount + (myTypeInfo.isEllipsis ? 1 : 0);
    String qualifiedName = PsiNameHelper.getQualifiedClassName(typeText, false);
    int depth = myFirstPassData.getInnerDepth(qualifiedName);
    boolean atWildcard = false;
    if (path == null) {
      if (depth == 0 || arrayLevel > 0) {
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
      byte[] result = new byte[depth];
      Arrays.fill(result, TypeAnnotationContainer.Collector.ENCLOSING_CLASS);
      return result;
    }
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    int length = path.getLength();
    for (int i = 0; i < length; i++) {
      byte step = (byte)path.getStep(i);
      switch (step) {
        case TypePath.INNER_TYPE:
          if (depth == 0) return null;
          depth--;
          break;
        case TypePath.ARRAY_ELEMENT:
          if (arrayLevel <= 0 || atWildcard) return null;
          arrayLevel--;
          result.write(TypeAnnotationContainer.Collector.ARRAY_ELEMENT);
          break;
        case TypePath.WILDCARD_BOUND:
          if (!atWildcard) return null;
          atWildcard = false;
          result.write(TypeAnnotationContainer.Collector.WILDCARD_BOUND);
          break;
        case TypePath.TYPE_ARGUMENT:
          if (atWildcard || arrayLevel > 0) return null;
          while (depth-- > 0) {
            result.write(TypeAnnotationContainer.Collector.ENCLOSING_CLASS);
            typeText = PsiNameHelper.getOuterClassReference(typeText);
          }
          int argumentIndex = path.getStepArgument(i);
          String[] arguments = PsiNameHelper.getClassParametersText(typeText);
          if (argumentIndex >= arguments.length) return null;
          TypeInfo argument = TypeInfo.fromString(arguments[argumentIndex], false);
          arrayLevel = argument.arrayCount;
          typeText = argument.text;
          if (typeText.startsWith("? extends ")) {
            typeText = typeText.substring("? extends ".length());
            atWildcard = true;
          }
          else if (typeText.startsWith("? super ")) {
            typeText = typeText.substring("? super ".length());
            atWildcard = true;
          }
          qualifiedName = PsiNameHelper.getQualifiedClassName(typeText, false);
          depth = myFirstPassData.getInnerDepth(qualifiedName);
          result.write(TypeAnnotationContainer.Collector.TYPE_ARGUMENT);
          result.write(argumentIndex);
          break;
      }
    }
    if (!atWildcard && arrayLevel == 0) {
      while (depth-- > 0) {
        result.write(TypeAnnotationContainer.Collector.ENCLOSING_CLASS);
      }
    }
    return result.toByteArray();
  }
}
