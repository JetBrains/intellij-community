// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/**
 * Adds actions to "Attach sources" notification panel
 *
 * @see com.intellij.codeInsight.daemon.impl.AttachSourcesNotificationProvider
 */
public interface AttachSourcesProvider {

  @NotNull @Unmodifiable
  Collection<? extends AttachSourcesAction> getActions(@NotNull List<? extends LibraryOrderEntry> orderEntries,
                                                       @NotNull PsiFile psiFile);

  interface AttachSourcesAction {

    @Nls(capitalization = Nls.Capitalization.Title) String getName();

    @NlsContexts.LinkLabel String getBusyText();

    @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile);
  }

  /**
   * This marker interface means what this action will be shown only if it is single action.
   */
  interface LightAttachSourcesAction extends AttachSourcesAction { }
}
