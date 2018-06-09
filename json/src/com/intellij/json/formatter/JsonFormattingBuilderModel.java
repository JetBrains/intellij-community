package com.intellij.json.formatter;

import com.intellij.formatting.*;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.json.JsonElementTypes.*;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingBuilderModel implements FormattingModelBuilder {
  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    final JsonBlock block = new JsonBlock(null, element.getNode(), settings, null, Indent.getNoneIndent(), null);
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  static SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
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
