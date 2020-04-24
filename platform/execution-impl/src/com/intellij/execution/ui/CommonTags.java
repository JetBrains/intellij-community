// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;

public class CommonTags {

  public static <S extends RunConfiguration> SettingsEditorFragment<S, ?> parallelRun() {
    return new TagFragment<>("runParallel",
                             ExecutionBundle.message("run.configuration.allow.running.parallel"),
                             ExecutionBundle.message("group.operating.system"),
                             s -> s.isAllowRunningInParallel(),
                             (s, aBoolean) -> s.setAllowRunningInParallel(aBoolean)
    );
  }
}
