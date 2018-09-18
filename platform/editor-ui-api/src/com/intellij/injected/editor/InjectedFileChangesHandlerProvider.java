// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.injected.editor;


import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Extension point for languages that require specific handling of changes in the <b>fragment-editor</b>
 *
 * If an implementation is not defined for the particular language
 * then {@link com.intellij.psi.impl.source.tree.injected.changesHandler.CommonInjectedFileChangesHandler} will be used by default
 *
 * @since 2018.3
 */
@ApiStatus.Experimental
public interface InjectedFileChangesHandlerProvider {
  LanguageExtension<InjectedFileChangesHandlerProvider> EP =
    new LanguageExtension<>("com.intellij.editor.injectedFileChangesHandlerProvider");

  InjectedFileChangesHandler createFileChangesHandler(List<PsiLanguageInjectionHost.Shred> shreds,
                                                      Editor hostEditor,
                                                      Document newDocument,
                                                      PsiFile injectedFile);
}
