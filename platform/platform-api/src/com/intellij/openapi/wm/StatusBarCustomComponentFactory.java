/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

/**
 * @deprecated use StatusBarWidget instead
 */
@Deprecated
public abstract class StatusBarCustomComponentFactory<T extends JComponent> implements EventListener {
  public static final ExtensionPointName<StatusBarCustomComponentFactory> EP_NAME = ExtensionPointName.create("com.intellij.statusBarComponent");

  public abstract T createComponent(@NotNull final StatusBar statusBar);

  public void disposeComponent(@NotNull StatusBar statusBar, @NotNull final T c) {
  }
}
