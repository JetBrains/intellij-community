// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

public interface PluginVersionChecker {

  ExtensionPointName<PluginVersionChecker> EP_NAME = new ExtensionPointName<>("com.intellij.pluginVersionChecker");

  /** Returns a priority for the checker, which is used to pick a checker if there are more than one. */
  int getPriority();

  /** Returns a pair of:
   *   the latest Kotlin plugin version for the current build number and channel;
   *   and a plugin download host.
   *
   * Android Studio: when there is a new version, Android Studio sets an absolute download url in the returned
   * plugin descriptor. The url is used by the plugin downloader to download the new version. In order for the
   * plugin downloader to use the download url, the returned host couldn't be null, even for the default host.
   *
   * @param host plugin download host, null means the default host
   * @return a nonnull plugin descriptor if there is a newer version to advertise, and null otherwise
   * @throws PluginVersionCheckFailed if check fails
   */
  Pair<IdeaPluginDescriptor, String> getLatest(@Nullable String currentVersion, @Nullable String host) throws PluginVersionCheckFailed;
}