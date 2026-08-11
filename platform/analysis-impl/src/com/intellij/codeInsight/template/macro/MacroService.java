// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Macro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.containers.MultiMap;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the registered {@link Macro} extensions indexed by name.
 */
@Service(Service.Level.APP)
final class MacroService {
  private volatile MultiMap<String, Macro> myMacroTable;

  MacroService(@NotNull CoroutineScope coroutineScope) {
    Macro.EP_NAME.addExtensionPointListener(coroutineScope, new ExtensionPointListener<Macro>() {
      @Override
      public void extensionAdded(@NotNull Macro extension, @NotNull PluginDescriptor pluginDescriptor) {
        myMacroTable.putValue(extension.getName(), extension);
      }

      @Override
      public void extensionRemoved(@NotNull Macro extension, @NotNull PluginDescriptor pluginDescriptor) {
        myMacroTable.remove(extension.getName(), extension);
      }
    });
  }

  public static @NotNull MacroService getInstance() {
    return ApplicationManager.getApplication().getService(MacroService.class);
  }

  public @NotNull MultiMap<String, Macro> getMacroTable() {
    MultiMap<String, Macro> table = myMacroTable;
    if (table == null) {
      // Build into a local first and publish only on success, so a ProcessCanceledException thrown while
      // instantiating the extensions is not cached - the next call recomputes (IJPL-247558).
      MultiMap<String, Macro> result = MultiMap.create();
      for (Macro macro : Macro.EP_NAME.getExtensionList()) {
        result.putValue(macro.getName(), macro);
      }
      table = result;
      myMacroTable = table;
    }
    return table;
  }
}
