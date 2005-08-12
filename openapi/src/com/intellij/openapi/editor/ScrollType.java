/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

public final class ScrollType {
  public static final ScrollType RELATIVE = new ScrollType("RELATIVE");
  public static final ScrollType CENTER = new ScrollType("CENTER");
  public static final ScrollType MAKE_VISIBLE = new ScrollType("MAKE_VISIBLE");
  public static final ScrollType CENTER_UP = new ScrollType("CENTER_UP");
  public static final ScrollType CENTER_DOWN = new ScrollType("CENTER_DOWN");

  private final String myDebugName;

  private ScrollType(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
