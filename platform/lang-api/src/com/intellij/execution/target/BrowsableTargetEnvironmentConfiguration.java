// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ActionListener;

public interface BrowsableTargetEnvironmentConfiguration {

  @NotNull <T extends Component> ActionListener createBrowser(@NotNull Project project,
                                                              @NlsContexts.DialogTitle String title,
                                                              @NotNull TextComponentAccessor<T> textComponentAccessor,
                                                              @NotNull T component);
}
