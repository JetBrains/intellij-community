/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.startup;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 * <p>
 * If the activity implements {@link com.intellij.openapi.project.DumbAware} it will be started in a pooled thread under 'Loading Project' dialog,
 * otherwise it will be started in the dispatch thread after the initialization
 */
public interface StartupActivity {

  ExtensionPointName<StartupActivity> POST_STARTUP_ACTIVITY = ExtensionPointName.create("com.intellij.postStartupActivity");

  void runActivity(@NotNull Project project);
}
