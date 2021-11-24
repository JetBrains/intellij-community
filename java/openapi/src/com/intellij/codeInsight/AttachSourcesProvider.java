// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Adds actions to "Attach sources" notification panel
 * @see com.intellij.codeInsight.daemon.impl.AttachSourcesNotificationProvider
 */
public interface AttachSourcesProvider {
  @NotNull Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, PsiFile psiFile);

  interface AttachSourcesAction {
    @Nls(capitalization = Nls.Capitalization.Title) String getName();
    @NlsContexts.LinkLabel String getBusyText();
    ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile);
  }

  /**
   * This marker interface means what this action will be shown only if it is single action.
   */
  interface LightAttachSourcesAction extends AttachSourcesAction { }
}
