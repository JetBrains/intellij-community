// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.formatter;

import com.intellij.formatting.*;
import com.intellij.json.JsonLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonElementTypes.*;
import static com.intellij.json.split.JsonBackendExtensionSuppressorKt.shouldDoNothingInBackendMode;

public final class JsonFormattingBuilderModel implements FormattingModelBuilder {
  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    if (shouldDoNothingInBackendMode()) return Formatter.getInstance().createDummyFormattingModel(formattingContext.getPsiElement());

    CodeStyleSettings settings = formattingContext.getCodeStyleSettings();
    JsonCodeStyleSettings customSettings = settings.getCustomSettings(JsonCodeStyleSettings.class);
    SpacingBuilder spacingBuilder = createSpacingBuilder(settings);
    final JsonBlock block =
      new JsonBlock(null, formattingContext.getNode(), customSettings, null, Indent.getSmartIndent(Indent.Type.CONTINUATION), null,
                    spacingBuilder);
    return FormattingModelProvider.createFormattingModelForPsiFile(formattingContext.getContainingFile(), block, settings);
  }

  static @NotNull SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    final JsonCodeStyleSettings jsonSettings = settings.getCustomSettings(JsonCodeStyleSettings.class);
    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JsonLanguage.INSTANCE);

    final int spacesBeforeComma = commonSettings.SPACE_BEFORE_COMMA ? 1 : 0;
    final int spacesBeforeColon = jsonSettings.SPACE_BEFORE_COLON ? 1 : 0;
    final int spacesAfterColon = jsonSettings.SPACE_AFTER_COLON ? 1 : 0;

    return new SpacingBuilder(settings, JsonLanguage.INSTANCE)
      .before(COLON).spacing(spacesBeforeColon, spacesBeforeColon, 0, false, 0)
      .after(COLON).spacing(spacesAfterColon, spacesAfterColon, 0, false, 0)
      .withinPair(L_BRACKET, R_BRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS, true)
      .withinPair(L_CURLY, R_CURLY).spaceIf(commonSettings.SPACE_WITHIN_BRACES, true)
      .before(COMMA).spacing(spacesBeforeComma, spacesBeforeComma, 0, false, 0)
      .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA);
  }
}
