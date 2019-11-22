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
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 * @deprecated Please use PsiCommentImpl directly
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
public class PsiCoreCommentImpl extends PsiCommentImpl {
  public PsiCoreCommentImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }
}
