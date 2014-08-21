package com.intellij.json.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.json.JsonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.json.JsonElementTypes.*;

/**
 * @author Mikhail Golubev
 */
public class JsonFormattingBuilderModel implements FormattingModelBuilder {
  private static final Logger LOG = Logger.getInstance(JsonFormattingBuilderModel.class);

  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("PSI Tree:\n" + DebugUtil.psiToString(element, false));
    }
    LOG.debug("Right margin: " + settings.getRightMargin(JsonLanguage.INSTANCE));
    JsonBlock block = new JsonBlock(null, element.getNode(), settings, null, Indent.getNoneIndent(), null);
    if (LOG.isDebugEnabled()) {
      StringBuilder builder = new StringBuilder();
      FormattingModelDumper.dumpFormattingModel(block, 2, builder);
      LOG.debug("Format Model:\n" + builder.toString());
    }
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  // create spacing model once for all subsequent blocks
  static SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    JsonCodeStyleSettings jsonSettings = settings.getCustomSettings(JsonCodeStyleSettings.class);
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JsonLanguage.INSTANCE);


    int spacesBeforeComma = commonSettings.SPACE_BEFORE_COMMA ? 1 : 0;
    int spacesBeforeColon = jsonSettings.SPACE_BEFORE_COLON ? 1 : 0;
    int spacesAfterColon = jsonSettings.SPACE_AFTER_COLON ? 1 : 0;
    // not allow to keep line breaks before colon/comma, because it looks horrible

    return new SpacingBuilder(settings, JsonLanguage.INSTANCE)
      .before(COLON).spacing(spacesBeforeColon, spacesBeforeColon, 0, false, 0)
      .after(COLON).spacing(spacesAfterColon, spacesAfterColon, 0, false, 0)
      .withinPair(L_BRACKET, R_BRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
      .withinPair(L_CURLY, R_CURLY).spaceIf(jsonSettings.SPACE_WITHIN_BRACES)
      .before(COMMA).spacing(spacesBeforeComma, spacesBeforeComma, 0, false, 0)
      .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA);
  }
}
