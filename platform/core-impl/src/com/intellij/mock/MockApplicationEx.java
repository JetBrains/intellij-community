// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link MockApplication}
 */
@Deprecated
public class MockApplicationEx extends MockApplication {
  public MockApplicationEx(@NotNull Disposable parentDisposable) {
    super(parentDisposable);

    Extensions.setRootArea(getExtensionArea());
  }
}
