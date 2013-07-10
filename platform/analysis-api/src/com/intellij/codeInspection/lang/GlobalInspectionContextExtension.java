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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GlobalInspectionContextExtension<T> {
  @NotNull
  Key<T> getID();

  void performPreRunActivities(@NotNull List<Tools> globalTools,
                               @NotNull List<Tools> localTools,
                               @NotNull GlobalInspectionContext context);
  void performPostRunActivities(@NotNull List<InspectionToolWrapper> inspections, @NotNull GlobalInspectionContext context);

  void cleanup();
}
