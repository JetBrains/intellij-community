// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.util.function.Consumer

@ApiStatus.Internal
interface SearchEverywhereResultsNotifier {
  var notifyCallback: Consumer<@NotNull @Nls String>?
}
