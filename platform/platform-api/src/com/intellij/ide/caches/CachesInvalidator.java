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
package com.intellij.ide.caches;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public abstract class CachesInvalidator {
  public static final ExtensionPointName<CachesInvalidator> EP_NAME = ExtensionPointName.create("com.intellij.cachesInvalidator");


  /**
   * @return description of the files to be cleared, shown in the warning dialog to the user.
   *         When to use: when invalidation will lead to the loss of a potentially valuable to the user information, e.g. Local History.
   *         Do not use:  when caches are easily re-buildable and doesn't contain user's data (to avoid unnecessary confusion). 
   */
  @Nullable
  public String getDescription() { return null; } 
  
  /**
   * The method should not consume significant time.
   * All the clearing operations should be executed after IDE relaunches.
   */
  public abstract void invalidateCaches();
}
