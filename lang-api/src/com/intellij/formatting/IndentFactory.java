/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.formatting;

/**
 * Internal interface for creating indent instances.
 */

interface IndentFactory {
  public abstract Indent getNormalIndent();
  public abstract Indent getNoneIndent();
  public abstract Indent getAbsoluteNoneIndent();
  public abstract Indent getAbsoluteLabelIndent();
  public abstract Indent getLabelIndent();
  public abstract Indent getContinuationIndent();
  public abstract Indent getContinuationWithoutFirstIndent();//is default
  public abstract Indent getSpaceIndent(final int spaces);
}
