package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.MixinExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DeclarationRangeUtil {
  public static Map<Class,DeclarationRangeHandler> ourDeclarationRangeRegistry = new HashMap<Class, DeclarationRangeHandler>();

  private DeclarationRangeUtil() {
  }

  public static void setDeclarationHandler(@NotNull Class clazz, DeclarationRangeHandler handler) {
    ourDeclarationRangeRegistry.put(clazz, handler);
  }

  public static @NotNull TextRange getDeclarationRange(PsiElement container) {
    final TextRange textRange = getPossibleDeclarationAtRange(container);
    assert textRange != null :"Declaration range is invalid for "+container.getClass();
    return textRange;
  }

  public static @Nullable
  TextRange getPossibleDeclarationAtRange(final PsiElement container) {
    DeclarationRangeHandler handler = MixinExtension.getInstance(DeclarationRangeHandler.EP_NAME, container);
    if (handler != null) {
      return handler.getDeclarationRange(container);
    }
    else {
      for(Class clazz:ourDeclarationRangeRegistry.keySet()) {
        if (clazz.isInstance(container)) {
          final DeclarationRangeHandler handler2 = ourDeclarationRangeRegistry.get(clazz);
          if (handler2 != null) return handler2.getDeclarationRange(container);
        }
      }

      return null;
    }
  }

}