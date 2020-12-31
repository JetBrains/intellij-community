// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class ParameterInfoControllerProvider {
  public static final ExtensionPointName<ParameterInfoControllerProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.codeInsight.parameterInfo.controller.provider");

  private static final Logger LOG = Logger.getInstance(ParameterInfoControllerProvider.class);

  private static Set<Editor> editorsWithControllers = null;

  public final @Nullable ParameterInfoControllerBase create(@NotNull Project project,
                                                            @NotNull Editor editor,
                                                            int lbraceOffset,
                                                            Object[] descriptors,
                                                            Object highlighted,
                                                            PsiElement parameterOwner,
                                                            @NotNull ParameterInfoHandler<? extends PsiElement, ?> handler,
                                                            boolean showHint,
                                                            boolean requestFocus) {
    ParameterInfoControllerBase infoControllerBase =
      doCreate(project, editor, lbraceOffset, descriptors, highlighted, parameterOwner, handler, showHint, requestFocus);

    if (infoControllerBase != null) {
      rememberEditor(editor);
    }

    return infoControllerBase;
  }

  public static void rememberEditor(@NotNull Editor editor) {
    if (editorsWithControllers == null) {
      LOG.debug("create map of weak references to remember all editors with custom parameter info controllers and attach EP-listener");
      editorsWithControllers = ContainerUtil.createWeakSet();  // Weak... to avoid memory leaks caused by keeping disposed editors in memory
      EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
        @Override
        public void extensionRemoved(@NotNull ParameterInfoControllerProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
          removeControllersFromAllEditors();
        }
      }, null);
    }

    if (!editorsWithControllers.contains(editor)) {
      LOG.debug("remember editor " + editor);
      editorsWithControllers.add(editor);
    }
  }

  public static void removeControllersFromAllEditors() {
    LOG.debug("remove all parameter info controllers from all editors because an extension was removed");
    for (Editor editor : editorsWithControllers) {
      if (editor != null) {
        for (ParameterInfoControllerBase controller : ParameterInfoControllerBase.getAllControllers(editor)) {
          Disposer.dispose(controller);
        }
      }
    }
  }

  protected abstract @Nullable ParameterInfoControllerBase doCreate(@NotNull Project project,
                                                                    @NotNull Editor editor,
                                                                    int lbraceOffset,
                                                                    Object[] descriptors,
                                                                    Object highlighted,
                                                                    PsiElement parameterOwner,
                                                                    @NotNull ParameterInfoHandler<? extends PsiElement, ?> handler,
                                                                    boolean showHint,
                                                                    boolean requestFocus);
}
