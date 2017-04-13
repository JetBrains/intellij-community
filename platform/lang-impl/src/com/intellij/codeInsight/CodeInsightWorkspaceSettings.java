/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author peter
 */
@State(name = "CodeInsightWorkspaceSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class CodeInsightWorkspaceSettings implements PersistentStateComponent<CodeInsightWorkspaceSettings> {
  public boolean optimizeImportsOnTheFly = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;

  @TestOnly
  public void setOptimizeImportsOnTheFly(boolean optimizeImportsOnTheFly, Disposable parentDisposable) {
    boolean prev = this.optimizeImportsOnTheFly;
    this.optimizeImportsOnTheFly = optimizeImportsOnTheFly;
    Disposer.register(parentDisposable, () -> {
      this.optimizeImportsOnTheFly = prev;
    });
  }

  public static CodeInsightWorkspaceSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CodeInsightWorkspaceSettings.class);
  }


  @Override
  public void loadState(CodeInsightWorkspaceSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nullable
  @Override
  public CodeInsightWorkspaceSettings getState() {
    return this;
  }
}
