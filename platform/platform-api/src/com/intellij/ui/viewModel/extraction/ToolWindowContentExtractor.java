// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

 /**
 * This extension point is used to allow or disallow synchronization of content (tabs)
 * in Run and Debug tool windows in Code With Me and/or JetBrains Gateway (remote development).
 * In simple case you can just use {@link #SYNC_TAB_TO_GUEST} to mark content or process handler to be synchronized.
 * If your content contains virtual files that must be visible by guests (i.e. in editors)
 * you should mark them with {@link #FILE_VISIBLE_FOR_GUEST}.
 */
@ApiStatus.Experimental
public interface ToolWindowContentExtractor {
  ExtensionPointName<ToolWindowContentExtractor> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowContentExtractor");
  Key<Boolean> SYNC_TAB_TO_GUEST = Key.create("ToolWindowContentExtractor.SyncTabToGuest");
  Key<Boolean> FILE_VISIBLE_FOR_GUEST = Key.create("ToolWindowContentExtractor.FileVisibleForGuest");
  
  boolean isApplicable(@NotNull Content content);
  
  boolean syncContentToGuests(@NotNull Content content);
}
