// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;


import com.intellij.injected.editor.InjectedFileChangesHandler;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * @since 2018.3
 */
@ApiStatus.Experimental
public interface InjectedFileChangesHandlerProvider {
  LanguageExtension<InjectedFileChangesHandlerProvider> EP =
    new LanguageExtension<>("com.intellij.editor.injectedFileChangesHandlerProvider");

  InjectedFileChangesHandler createFileChangesHandler(List<PsiLanguageInjectionHost.Shred> shreds,
                                                      Editor editor,
                                                      Document newDocument,
                                                      PsiFile injectedFile);
}
