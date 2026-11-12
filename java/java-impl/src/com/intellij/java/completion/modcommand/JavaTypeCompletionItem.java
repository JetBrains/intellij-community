// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/**
 * A completion item of a Java class or type.
 */
@NotNullByDefault
public sealed interface JavaTypeCompletionItem extends ModCompletionItem permits ClassReferenceCompletionItem, PsiTypeCompletionItem {
  @Contract(pure = true)
  JavaTypeCompletionItem withPriority(double priority);

  @Contract(pure = true)
  JavaTypeCompletionItem withPresentableName(@Nullable String forcedPresentableName);

  /**
   * @return the item that shows {@code {...}} in the tail for an interface or an abstract class: it also creates an anonymous class
   */
  @Contract(pure = true)
  JavaTypeCompletionItem indicateAnonymous();

  PsiType getType();

  static ClassReferenceCompletionItem create(PsiClass psiClass) {
    return new ClassReferenceCompletionItem(psiClass);
  }

  /**
   * @return the class item for a resolved class type without type arguments, so that it equals the item of the same class
   * from another provider; the type item for any other type
   */
  static JavaTypeCompletionItem create(PsiType type) {
    if (type instanceof PsiClassType classType && classType.getParameterCount() == 0 &&
        classType.resolve() instanceof PsiClass psiClass) {
      return create(psiClass);
    }
    return new PsiTypeCompletionItem(type, lookupString(type));
  }

  /**
   * @return the item of {@code type}: an array is never a class item
   */
  static PsiTypeCompletionItem create(PsiArrayType type) {
    return new PsiTypeCompletionItem(type, lookupString(type));
  }

  /**
   * @return the name of the component type of {@code type}, without type arguments when its class resolves:
   * the item inserts them on its own
   */
  private static String lookupString(PsiType type) {
    PsiType typeToPresent = type.getDeepComponentType();
    if (typeToPresent instanceof PsiClassType classType && classType.resolve() != null) {
      typeToPresent = classType.rawType();
    }
    return typeToPresent.getPresentableText();
  }
}
