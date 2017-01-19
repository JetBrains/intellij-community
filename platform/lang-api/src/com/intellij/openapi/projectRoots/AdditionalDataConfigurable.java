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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nullable;

public interface AdditionalDataConfigurable extends UnnamedConfigurable {
  void setSdk(Sdk sdk);

  /**
   *  In case of non-null value the component returned by {@link #createComponent()} will be added as a tab to myTabbedPane in SdkEditor
   */
  @Nullable
  default String getTabName() {
    return null;
  }
}
