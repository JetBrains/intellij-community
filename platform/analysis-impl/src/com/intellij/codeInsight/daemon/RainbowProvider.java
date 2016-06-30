/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class RainbowProvider {
  static ExtensionPointName<RainbowProvider> EP_NAME = ExtensionPointName.create("com.intellij.rainbowProvider");

  @NotNull
  public static RainbowProvider[] getRainbowFileProcessors() {
    return Extensions.getExtensions(EP_NAME);
  }

  public static void initRainbow() {
    for (RainbowProvider processor : getRainbowFileProcessors()) {
      processor.init();
    }
  }

  public abstract void init();

  public abstract boolean isValidContext(@NotNull final PsiFile file);

  public abstract List<HighlightInfo> getHighlights(@NotNull PsiFile file,
                                                    @NotNull RainbowHighlighter highlighter,
                                                    @NotNull ProgressIndicator progress);
}
