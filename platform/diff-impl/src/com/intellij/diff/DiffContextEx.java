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
package com.intellij.diff;

import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

public abstract class DiffContextEx extends DiffContext {
  /*
   * Reopen current DiffRequest.
   *
   * perform the same procedure as on switching between DiffRequests or between DiffViewers.
   * this can be used, if some change in request or settings was made, and we need to reopen DiffViewer to apply them.
   */
  @CalledInAwt
  public abstract void reopenDiffRequest();

  /*
   * Drop cached DiffRequest version (if any) and reopen current DiffRequest.
   *
   * perform the same procedure as on opening DiffRequests for the first time.
   * this can be used, if some change in request or settings was made, and we need to reload DiffRequest to apply them.
   */
  @CalledInAwt
  public abstract void reloadDiffRequest();

  /*
   * Show indeterminate progress near status panel.
   */
  @CalledInAwt
  public abstract void showProgressBar(boolean enabled);

  @CalledInAwt
  public abstract void setWindowTitle(@NotNull String title);
}
