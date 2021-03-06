// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This class is intended to combine all providers for batch usages.
 */
public final class CompoundIconProvider extends IconProvider {
  private static final IconProvider INSTANCE = new CompoundIconProvider();
  private static final Logger LOG = Logger.getInstance(CompoundIconProvider.class);

  @Nullable
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element.isValid()) {
      for (IconProvider provider : EXTENSION_POINT_NAME.getExtensionList()) {
        ProgressManager.checkCanceled();
        try {
          Icon icon = provider.getIcon(element, flags);
          if (icon != null) {
            LOG.debug("icon found in ", provider);
            return icon;
          }
        }
        catch (IndexNotReadyException exception) {
          throw new ProcessCanceledException(exception);
        }
        catch (ProcessCanceledException exception) {
          throw exception;
        }
        catch (Exception exception) {
          LOG.warn("unexpected error in " + provider, exception);
        }
      }
      return element.getIcon(flags);
    }
    return null;
  }

  @Nullable
  public static Icon findIcon(@Nullable PsiElement element, int flags) {
    return element == null ? null : INSTANCE.getIcon(element, flags);
  }
}
