// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.client.ClientProjectSession;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

 /**
 * This extension point is used to allow or disallow synchronization of content (tabs)
 * in Run and Debug tool windows in Code With Me and/or JetBrains Gateway (remote development).
 * For Remote Development, by default, lux will be used to transfer the content.
 * Otherwise, in simple cases you can just use {@link #SYNC_TAB_TO_REMOTE_CLIENTS} to mark content or process handler to be synchronized.
 * If your content contains virtual files that must be visible (i.e. in editors)
 * you should mark them with {@link #FILE_VISIBLE_FOR_REMOTE_CLIENTS}.
 */
@Internal
public interface ToolWindowContentExtractor {
  ExtensionPointName<ToolWindowContentExtractor> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowContentExtractor");
  Key<Boolean> SYNC_TAB_TO_REMOTE_CLIENTS = Key.create("ToolWindowContentExtractor.SyncTabToGuest");
  Key<Boolean> FILE_VISIBLE_FOR_REMOTE_CLIENTS = Key.create("ToolWindowContentExtractor.FileVisibleForGuest");

  boolean isApplicable(@NotNull Content content, @NotNull ClientProjectSession session);

  boolean syncContentToRemoteClient(@NotNull Content content);
}
