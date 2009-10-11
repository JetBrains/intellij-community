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

package com.intellij.facet;

import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public final class FacetTypeId<F extends Facet> {
  private final String myDebugName;

  @Deprecated
  /**
   * @deprecated use {@link FacetTypeId#FacetTypeId(String)} instead
   */
  public FacetTypeId() {
    this("unknown");
  }

  public FacetTypeId(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
