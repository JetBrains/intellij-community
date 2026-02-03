// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * An item displayed in ListChooseByNameModel.
 */
public interface ChooseByNameItem {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  String getName();
  
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getDescription();
}
