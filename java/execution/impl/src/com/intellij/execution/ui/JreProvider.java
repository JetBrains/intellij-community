/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point for providing custom jre to be shown at run configuration control.
 * 
 * @author Konstantin Bulenkov
 */
public interface JreProvider {

  ExtensionPointName<JreProvider> EP_NAME = new ExtensionPointName<>("com.intellij.jreProvider");
  
  @NotNull
  @Contract(pure=true)
  String getJrePath();

  @Contract(pure=true)
  default boolean isAvailable() {
    return true;
  }

  @Contract(pure=true)
  default @NlsSafe String getPresentableName() {
    return getJrePath();
  }

  default @NonNls String getID() {
    return null;
  }
}
