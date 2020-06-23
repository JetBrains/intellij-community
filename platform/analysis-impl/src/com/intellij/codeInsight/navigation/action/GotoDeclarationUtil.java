// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.action;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GotoDeclarationUtil {

  private static final Logger LOG = Logger.getInstance(GotoDeclarationUtil.class);

  public static @NotNull PsiElement @Nullable [] findTargetElementsFromProviders(@NotNull Editor editor, int offset, PsiFile file) {
    PsiElement elementAt = file.findElementAt(TargetElementUtilBase.adjustOffset(file, editor.getDocument(), offset));
    return findTargetElementsFromProviders(elementAt, offset, editor);
  }

  public static @NotNull PsiElement @Nullable [] findTargetElementsFromProviders(@Nullable PsiElement elementAt,
                                                                                 int offset,
                                                                                 @NotNull Editor editor) {
    for (GotoDeclarationHandler handler : GotoDeclarationHandler.EP_NAME.getExtensionList()) {
      PsiElement[] result = handler.getGotoDeclarationTargets(elementAt, offset, editor);
      if (result != null && result.length > 0) {
        return assertNotNullElements(result, handler.getClass()) ? result : null;
      }
    }

    return PsiElement.EMPTY_ARRAY;
  }

  private static boolean assertNotNullElements(PsiElement @NotNull [] result, Class<?> clazz) {
    for (PsiElement element : result) {
      if (element == null) {
        PluginException.logPluginError(LOG,
                                       "Null target element is returned by 'getGotoDeclarationTargets' in " + clazz.getName(), null, clazz
        );
        return false;
      }
    }
    return true;
  }
}
