// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.injection;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This will work only in presence of intellij.platform.langInjection plugin.
 *
 * @author Dmitry Avdeev
 */
public abstract class ReferenceInjector extends Injectable {

  public static final ExtensionPointName<ReferenceInjector> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.referenceInjector");

  @Override
  public final Language getLanguage() {
    return null;
  }

  /**
   * Generated references should be soft ({@link PsiReference#isSoft()})
   */
  public abstract PsiReference @NotNull [] getReferences(@NotNull PsiElement element, final @NotNull ProcessingContext context, @NotNull TextRange range);

  public static ReferenceInjector findById(@NotNull String id) {
    return ContainerUtil.find(EXTENSION_POINT_NAME.getExtensionList(), injector -> id.equals(injector.getId()));
  }
}
