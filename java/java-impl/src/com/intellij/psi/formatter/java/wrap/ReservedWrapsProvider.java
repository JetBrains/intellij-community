/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java.wrap;

import com.intellij.formatting.Wrap;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Defines contract for using predefined reserved wraps.
 * <p/>
 * <b>Note:</b> this interface is introduced for encapsulating legacy {@link AbstractJavaBlock#getReservedWrap(IElementType)}
 * method and most probably will be removed as soon as formatting stuff is refactored.
 *
 * @author Denis Zhdanov
 * @since Apr 21, 2010 3:43:17 PM
 */
public interface ReservedWrapsProvider {

  /**
   * Allows to retrieve predefined {@link Wrap} object for the given element type if any.
   *
   * @param elementType   target element type
   * @return              predefined wrap for the given element type if any; <code>null</code> otherwise
   */
  @Nullable
  Wrap getReservedWrap(IElementType elementType);
}
