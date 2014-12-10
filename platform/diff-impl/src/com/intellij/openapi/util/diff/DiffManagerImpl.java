/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.SimpleDiffRequestChain;
import com.intellij.openapi.util.diff.impl.DiffWindow;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.binary.BinaryDiffTool;
import com.intellij.openapi.util.diff.tools.dir.DirDiffTool;
import com.intellij.openapi.util.diff.tools.external.ExternalDiffTool;
import com.intellij.openapi.util.diff.tools.oneside.OnesideDiffTool;
import com.intellij.openapi.util.diff.tools.simple.SimpleDiffTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffManagerImpl extends DiffManagerEx {
  private static final Logger LOG = Logger.getInstance(DiffManagerImpl.class);

  @Override
  public void showDiff(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints) {
    DiffRequestChain requestChain = new SimpleDiffRequestChain(Collections.singletonList(request));
    showDiff(project, requestChain, hints);
  }

  @Override
  public void showDiff(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints) {
    if (ExternalDiffTool.isDefault()) {
      ExternalDiffTool.show(project, requests, hints);
      return;
    }

    showDiffBuiltin(project, requests, hints);
  }

  @Override
  public void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints) {
    DiffRequestChain requestChain = new SimpleDiffRequestChain(Collections.singletonList(request));
    showDiffBuiltin(project, requestChain, hints);
  }

  @Override
  public void showDiffBuiltin(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints) {
    new DiffWindow(project, requests, hints).show();
  }

  @NotNull
  @Override
  public List<DiffTool> getDiffTools() {
    // TODO: we need some kind of (configurable?) priorities here
    // TODO: we could also want to 'hide' some existing tool
    //       ex: hide default binary diff, if there'll be a brand-new image comparator plugin.
    //           or hide default SimpleDiffTool, if there are CommentedSimpleDiffTool.INSTANCE came from plugin
    List<DiffTool> result = new ArrayList<DiffTool>();

    result.add(SimpleDiffTool.INSTANCE);
    result.add(OnesideDiffTool.INSTANCE);
    result.add(BinaryDiffTool.INSTANCE);
    result.add(DirDiffTool.INSTANCE);

    for (DiffTool tool : DiffTool.EP_NAME.getExtensions()) {
      result.add(tool);
    }

    return result;
  }
}
