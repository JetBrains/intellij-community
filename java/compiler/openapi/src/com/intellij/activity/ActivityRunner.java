/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.activity;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * TODO
 * get compiled files status
 *
 * @author Vladislav.Soroka
 * @since 4/29/2016
 */
public abstract class ActivityRunner {

  public static final ExtensionPointName<ActivityRunner> EP_NAME = ExtensionPointName.create("com.intellij.activityRunner");

  public abstract void run(@NotNull Project project,
                           @NotNull ActivityContext context,
                           @Nullable ActivityStatusNotification callback,
                           @NotNull Collection<? extends Activity> activities);

  public void run(@NotNull Project project,
                  @NotNull ActivityContext context,
                  @Nullable ActivityStatusNotification callback,
                  @NotNull Activity... activities) {
    run(project, context, callback, Arrays.asList(activities));
  }

  public abstract boolean canRun(@NotNull Activity activity);

  public abstract ExecutionEnvironment createActivityExecutionEnvironment(@NotNull Project project, @NotNull RunActivity activity);
}
