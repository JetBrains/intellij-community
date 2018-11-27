// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.Novelty;
import org.jetbrains.annotations.NotNull;

public interface IInternalPage extends IPage {

  IPage getChild(@NotNull Novelty.Accessor novelty, final int index);
}
