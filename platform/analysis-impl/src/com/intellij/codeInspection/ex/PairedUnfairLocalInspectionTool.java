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
package com.intellij.codeInspection.ex;

import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 4/17/13
 */
public interface PairedUnfairLocalInspectionTool extends UnfairLocalInspectionTool {
  /**
   * @return {@link com.intellij.codeInspection.LocalInspectionTool#getShortName()} of
   *         a tool to be run instead of this tool in batch mode.
   *         The returned value can be a short name of this inspection tool, in this case
   *         this unfair tool will be run in batch mode.
   */
  @NotNull
  String getInspectionForBatchShortName();
}
