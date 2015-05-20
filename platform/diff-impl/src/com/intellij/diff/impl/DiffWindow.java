/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.impl;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffWindow extends DiffWindowBase {
  @NotNull private final DiffRequestChain myRequestChain;

  public DiffWindow(@Nullable Project project, @NotNull DiffRequestChain requestChain, @NotNull DiffDialogHints hints) {
    super(project, hints);
    myRequestChain = requestChain;
  }

  @NotNull
  @Override
  protected DiffRequestProcessor createProcessor() {
    return new MyCacheDiffRequestChainProcessor(myProject, myRequestChain);
  }

  private class MyCacheDiffRequestChainProcessor extends CacheDiffRequestChainProcessor {
    public MyCacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
      super(project, requestChain);
    }

    @Override
    protected void setWindowTitle(@NotNull String title) {
      getWrapper().setTitle(title);
    }

    @Override
    protected void onAfterNavigate() {
      DiffUtil.closeWindow(getWrapper().getWindow(), true, true);
    }
  }
}
