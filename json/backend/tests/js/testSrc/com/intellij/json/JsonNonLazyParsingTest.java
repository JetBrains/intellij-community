// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.syntax.JsonLazyParsingKt;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.junit5.TestApplication;

import java.io.File;

/**
 * Runs all {@link JsonParsingTest} tests is non-lazy parsing mode is on ({@code -Djson.lazy.parsing=false}).
 * <p>
 * For tests that produce different PSI trees in non-lazy mode, place a {@code TestName_nonLazy.txt}
 * gold file alongside the standard {@code TestName.txt}. If no {@code _nonLazy} variant exists,
 * the standard gold file is used (validating that both modes produce identical trees).
 */
@TestApplication
public class JsonNonLazyParsingTest extends JsonParsingTest {

  @Override
  protected void checkResult(ParsingTestFixture fixture, String targetDataName, PsiFile file) {
    String nonLazyGoldFile = fixture.getFullDataPath() + File.separatorChar + targetDataName + "_nonLazy.txt";
    if (new File(nonLazyGoldFile).exists()) {
      fixture.checkResult(fixture.getFullDataPath(), targetDataName + "_nonLazy", file);
    }
    else {
      super.checkResult(fixture, targetDataName, file);
    }
  }

  @Override
  protected boolean isIgnore() {
    return JsonLazyParsingKt.getJsonLazyParsing();
  }
}
