// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.execution;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DefaultExecutionTarget extends ExecutionTarget {
  public static final ExecutionTarget INSTANCE = new DefaultExecutionTarget();

  private DefaultExecutionTarget() {
  }

  @NotNull
  @Override
  public String getId() {
    return "default_target";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return IdeBundle.message("node.default");
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean canRun(@NotNull RunnerAndConfigurationSettings configuration) {
    return true;
  }
}
