/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.ConfigurableBuilder;

public final class JavaErrorOptionsProvider extends ConfigurableBuilder implements ErrorOptionsProvider {
  public JavaErrorOptionsProvider() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    checkBox(JavaBundle.message("checkbox.suppress.with.suppresswarnings"),
             settings::isSuppressWarnings, settings::setSuppressWarnings);
  }
}