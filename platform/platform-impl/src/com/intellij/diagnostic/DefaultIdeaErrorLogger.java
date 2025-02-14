// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class DefaultIdeaErrorLogger {
  public static @Nullable MemoryKind getOOMErrorKind(@NotNull Throwable t) {
    var message = t.getMessage();

    if (t instanceof OutOfMemoryError) {
      if (message != null) {
        if (message.contains("unable to create") && message.contains("native thread")) return null;
        if (message.contains("Metaspace")) return MemoryKind.METASPACE;
        if (message.contains("direct buffer memory")) return MemoryKind.DIRECT_BUFFERS;
      }
      return MemoryKind.HEAP;
    }

    if (t instanceof VirtualMachineError && message != null && message.contains("CodeCache")) {
      return MemoryKind.CODE_CACHE;
    }

    return null;
  }

  public static @Nullable ErrorReportSubmitter findSubmitter(@NotNull Throwable t, @Nullable IdeaPluginDescriptor plugin) {
    if (t instanceof MessagePool.TooManyErrorsException || t instanceof AbstractMethodError) {
      return null;
    }

    List<ErrorReportSubmitter> reporters;
    try {
      reporters = ErrorReportSubmitter.EP_NAME.getExtensionList();
    }
    catch (Throwable ignored) {
      return null;
    }

    if (plugin != null) {
      for (var reporter : reporters) {
        var descriptor = reporter.getPluginDescriptor();
        if (descriptor != null && plugin.getPluginId().equals(descriptor.getPluginId())) {
          return reporter;
        }
      }
    }
    return getAndroidErrorReporter(); /* Android Studio: use Android instead of Jetbrains
    if (plugin == null || PluginManagerCore.isDevelopedByJetBrains(plugin)) {
      for (var reporter : reporters) {
        var descriptor = reporter.getPluginDescriptor();
        if (descriptor == null || PluginManagerCore.CORE_ID.equals(descriptor.getPluginId())) {
          return reporter;
        }
      }
    }
    */
  }
  private static ErrorReportSubmitter getAndroidErrorReporter() {
    try {
      return ContainerUtil.find(ErrorReportSubmitter.EP_NAME.getExtensionList(), reporter -> {
        PluginDescriptor pluginDescriptor = reporter.getPluginDescriptor();
        if (pluginDescriptor == null) {
          return false;
        }
        String pluginId = pluginDescriptor.getPluginId().getIdString();
        return pluginId.equals("org.jetbrains.android");
      });
    } catch (Throwable t) {
      return null;
    }
  }
}
