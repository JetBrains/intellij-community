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
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface JavaCompletionFeatures {
  @NonNls String SECOND_SMART_COMPLETION_CHAIN = "editing.completion.second.smarttype.chain";
  @NonNls String SECOND_SMART_COMPLETION_TOAR = "editing.completion.second.smarttype.toar";
  @NonNls String SECOND_SMART_COMPLETION_ASLIST = "editing.completion.second.smarttype.aslist";
  @NonNls String SECOND_SMART_COMPLETION_ARRAY_MEMBER = "editing.completion.second.smarttype.array.member";
  @NonNls String IMPORT_STATIC = "editing.completion.import.static";
}
