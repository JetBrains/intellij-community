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
package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;

/**
 * The alignment setting for a formatting model block. Blocks which return the same
 * alignment object instance from the <code>getAlignment</code> method
 * are aligned with each other.
 *
 * @see com.intellij.formatting.Block#getAlignment()
 * @see com.intellij.formatting.ChildAttributes#getAlignment()
 */

public abstract class Alignment {
  private static AlignmentFactory myFactory;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Alignment");

  static void setFactory(AlignmentFactory factory) {
    myFactory = factory;
  }

  /**
   * Creates an alignment object.
   *
   * @return the alignment object.
   */
  public static Alignment createAlignment() {
    return myFactory.createAlignment();
  }
}
