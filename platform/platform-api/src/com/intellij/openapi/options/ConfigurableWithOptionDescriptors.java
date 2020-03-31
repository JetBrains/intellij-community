// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.ide.ui.search.OptionDescription;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

@ApiStatus.Experimental
public interface ConfigurableWithOptionDescriptors {
  @NotNull
  List<OptionDescription> getOptionDescriptors(@NotNull String configurableId, @NotNull Function<? super String, String> nameConverter);
}
