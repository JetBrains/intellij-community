// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.wizard;

import com.intellij.ide.IdeCoreBundle;

public class CommitStepCancelledException extends CommitStepException {
  public CommitStepCancelledException() {
    super(IdeCoreBundle.message("commit.step.user.cancelled"));
  }
}
