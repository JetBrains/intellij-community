// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;

public final class CommonTags {

  public static <S extends RunConfiguration> SettingsEditorFragment<S, ?> parallelRun() {
    SettingsEditorFragment<S, ?> tag = SettingsEditorFragment.createTag("runParallel",
                                                                        ExecutionBundle
                                                                          .message("run.configuration.allow.running.parallel.tag"),
                                                                        ExecutionBundle.message("group.operating.system"),
                                                                        s -> s.isAllowRunningInParallel(),
                                                                        (s, value) -> s.setAllowRunningInParallel(value));
    tag.setActionHint(ExecutionBundle.message("allow.running.multiple.instances.of.the.application.simultaneously"));
    return tag;
  }
}
