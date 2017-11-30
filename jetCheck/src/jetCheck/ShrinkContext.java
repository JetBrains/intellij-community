// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetCheck;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
abstract class ShrinkContext {

  @NotNull
  abstract StructureNode getCurrentMinimalRoot();

  abstract boolean tryReplacement(@NotNull NodeId replacedId, @NotNull StructureElement replacement);
}
