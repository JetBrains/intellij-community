// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class ParsingDiagnostics {
  private ParsingDiagnostics() {
  }

  public static void registerParse(@NotNull PsiBuilder builder, @NotNull Language language, long parsingTimeNs){
    //noinspection IncorrectServiceRetrieving
    ParserDiagnosticsHandler handler = ApplicationManager.getApplication().getService(ParserDiagnosticsHandler.class);
    if( handler != null){
      handler.registerParse(builder, language, parsingTimeNs);
    }
  }

  public interface ParserDiagnosticsHandler{
    void registerParse(@NotNull PsiBuilder builder, @NotNull Language language, long parsingTimeNs);
  }
}
