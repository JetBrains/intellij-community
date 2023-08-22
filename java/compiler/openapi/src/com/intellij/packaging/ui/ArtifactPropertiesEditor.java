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
package com.intellij.packaging.ui;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class ArtifactPropertiesEditor implements UnnamedConfigurable {
  public static final Supplier<@Nls String> VALIDATION_TAB_POINTER =
    JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.validation");

  public static final Supplier<@Nls String> POST_PROCESSING_TAB_POINTER =
    JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.post.processing");

  public static final Supplier<@Nls String> PRE_PROCESSING_TAB_POINTER
    = JavaCompilerBundle.messagePointer("ArtifactPropertiesEditor.tab.pre.processing");

  public abstract @Nls String getTabName();

  @Override
  public abstract void apply();

  @Nullable
  public String getHelpId() {
    return null;
  }
}
