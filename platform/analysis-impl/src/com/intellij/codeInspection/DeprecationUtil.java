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
package com.intellij.codeInspection;

import org.jetbrains.annotations.NonNls;

public class DeprecationUtil {
  @NonNls public static final String DEPRECATION_SHORT_NAME = "Deprecation";
  @NonNls public static final String DEPRECATION_DISPLAY_NAME = InspectionsBundle.message("inspection.deprecated.display.name");
  @NonNls public static final String DEPRECATION_ID = "deprecation";

  @NonNls public static final String FOR_REMOVAL_SHORT_NAME = "MarkedForRemoval";
  @NonNls public static final String FOR_REMOVAL_DISPLAY_NAME = InspectionsBundle.message("inspection.marked.for.removal.display.name");
  @NonNls public static final String FOR_REMOVAL_ID = "removal";
}
