package com.intellij.codeInsight.completion.methodChains;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class ChainCompletionStringUtil {
  private ChainCompletionStringUtil() {}

  public static boolean isPrimitiveOrArray(final @Nullable String typeQName) {
    return typeQName != null && (typeQName.endsWith("[]") || PRIMITIVES_NAMES.contains(typeQName));
  }

  /**
   * CAUTION: isPrimitiveOrArrayOfPrimitives("java.lang.String") == true,
   *          isPrimitiveOrArrayOfPrimitives("java.lang.Object") == true
   *          isPrimitiveOrArrayOfPrimitives("java.lang.Class") == true
   */
  public static boolean isPrimitiveOrArrayOfPrimitives(final String typeQName) {
    if (typeQName == null) {
      return false;
    }
    return PRIMITIVES_NAMES.contains(deleteArraySigns(typeQName));
  }

  public static boolean isShortNamePrimitiveOrArrayOfPrimitives(final @Nullable String shortName) {
    if (shortName == null) {
      return false;
    }
    return PRIMITIVES_SHORT_NAMES.contains(deleteArraySigns(shortName));
  }

  private static String deleteArraySigns(final @NotNull String typeName) {
    String nameWithoutArraySign = typeName;
    while (nameWithoutArraySign.endsWith("[]")) {
      nameWithoutArraySign = nameWithoutArraySign.substring(0, nameWithoutArraySign.length() - 2);
    }
    return nameWithoutArraySign;
  }

  private static final Set<String> PRIMITIVES_NAMES = new HashSet<String>();

  static {
    fillPrimitivesNames(PsiType.BOOLEAN);
    fillPrimitivesNames(PsiType.INT);
    fillPrimitivesNames(PsiType.LONG);
    fillPrimitivesNames(PsiType.DOUBLE);
    fillPrimitivesNames(PsiType.FLOAT);
    fillPrimitivesNames(PsiType.SHORT);
    fillPrimitivesNames(PsiType.CHAR);
    fillPrimitivesNames(PsiType.BYTE);
    fillPrimitivesNames(PsiType.VOID);
    PRIMITIVES_NAMES.add(CommonClassNames.JAVA_LANG_STRING);
    PRIMITIVES_NAMES.add(CommonClassNames.JAVA_LANG_OBJECT);
    PRIMITIVES_NAMES.add(CommonClassNames.JAVA_LANG_CLASS);
  }

  private static void fillPrimitivesNames(final PsiPrimitiveType type) {
    PRIMITIVES_NAMES.add(type.getBoxedTypeName());
    PRIMITIVES_NAMES.add(type.getCanonicalText());
  }

  private static final Set<String> PRIMITIVES_SHORT_NAMES = new HashSet<String>();

  static {
    fillPrimitivesShortNames(PsiType.BOOLEAN);
    fillPrimitivesShortNames(PsiType.INT);
    fillPrimitivesShortNames(PsiType.LONG);
    fillPrimitivesShortNames(PsiType.DOUBLE);
    fillPrimitivesShortNames(PsiType.FLOAT);
    fillPrimitivesShortNames(PsiType.SHORT);
    fillPrimitivesShortNames(PsiType.CHAR);
    fillPrimitivesShortNames(PsiType.BYTE);
    fillPrimitivesShortNames(PsiType.VOID);
    PRIMITIVES_SHORT_NAMES.add(StringUtilRt.getShortName(CommonClassNames.JAVA_LANG_STRING));
    PRIMITIVES_SHORT_NAMES.add(StringUtilRt.getShortName(CommonClassNames.JAVA_LANG_OBJECT));
    PRIMITIVES_SHORT_NAMES.add(StringUtilRt.getShortName(CommonClassNames.JAVA_LANG_CLASS));
  }

  private static void fillPrimitivesShortNames(final PsiPrimitiveType type) {
    PRIMITIVES_SHORT_NAMES.add(StringUtilRt.getShortName(type.getBoxedTypeName()));
    PRIMITIVES_SHORT_NAMES.add(type.getCanonicalText());
  }

}
