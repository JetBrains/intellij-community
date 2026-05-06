// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Runs all {@link JsonParsingTest} tests is non-lazy parsing mode is on ({@code -Djson.lazy.parsing=false}).
 * <p>
 * For tests that produce different PSI trees in non-lazy mode, place a {@code TestName_nonLazy.txt}
 * gold file alongside the standard {@code TestName.txt}. If no {@code _nonLazy} variant exists,
 * the standard gold file is used (validating that both modes produce identical trees).
 */
public class JsonNonLazyParsingTest extends JsonParsingTest {

  @Override
  protected void checkResult(@NotNull String targetDataName, @NotNull PsiFile file) throws IOException {
    String nonLazyGoldFile = myFullDataPath + File.separatorChar + targetDataName + "_nonLazy.txt";
    if (new File(nonLazyGoldFile).exists()) {
      checkResult(myFullDataPath, targetDataName + "_nonLazy", file);
    }
    else {
      super.checkResult(targetDataName, file);
    }
  }

  @Override
  protected boolean isIgnore() {
    return JsonElementFactory.getJsonLazyParsingIJ();
  }
}
