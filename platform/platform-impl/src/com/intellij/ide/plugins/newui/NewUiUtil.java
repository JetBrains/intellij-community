// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NewUiUtil {

  private NewUiUtil() {
  }

  static @NotNull @NlsSafe String getUpdateVersionText(@Nullable String oldVersion,
                                                       @Nullable String newVersion) {
    return String.join(" ",
                       StringUtil.defaultIfEmpty(oldVersion, IdeBundle.message("plugin.info.unknown")),
                       UIUtil.rightArrow(),
                       StringUtil.defaultIfEmpty(newVersion, IdeBundle.message("plugin.info.unknown")));
  }

  static @NotNull @NlsSafe String getVersion(@NotNull IdeaPluginDescriptor oldDescriptor,
                                             @NotNull IdeaPluginDescriptor newDescriptor) {
    return getUpdateVersionText(oldDescriptor.getVersion(), newDescriptor.getVersion());
  }

  static boolean isDeleted(@Nullable IdeaPluginDescriptor descriptor) {
    return descriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)descriptor).isDeleted();
  }
}
