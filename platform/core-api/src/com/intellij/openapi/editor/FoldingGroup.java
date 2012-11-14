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
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

/**
 * {@link com.intellij.openapi.editor.FoldRegion}s with same FoldingGroup instances expand and collapse together.
 *
 * @author peter
 */
public class FoldingGroup {
  @NonNls private final String myDebugName;

  private FoldingGroup(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public static FoldingGroup newGroup(@NonNls String debugName) {
    return new FoldingGroup(debugName);
  }

  @Override
  public String toString() {
    return myDebugName;
  }
}
