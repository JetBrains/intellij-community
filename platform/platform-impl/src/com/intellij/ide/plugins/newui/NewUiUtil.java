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

  static @NotNull @NlsSafe String getVersion(@NotNull IdeaPluginDescriptor oldDescriptor,
                                             @NotNull IdeaPluginDescriptor newDescriptor) {
    return String.join(" ",
                       getVersion(oldDescriptor),
                       UIUtil.rightArrow(),
                       getVersion(newDescriptor));
  }

  private static @NotNull @NlsSafe String getVersion(@NotNull IdeaPluginDescriptor descriptor) {
    return StringUtil.defaultIfEmpty(descriptor.getVersion(),
                                     IdeBundle.message("plugin.info.unknown"));
  }

  static boolean isDeleted(@Nullable IdeaPluginDescriptor descriptor) {
    return descriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)descriptor).isDeleted();
  }
}
