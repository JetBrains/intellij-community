// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.syntax.JsonSyntaxElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterKt;
import org.jetbrains.annotations.NotNull;
import kotlin.Pair;

public class JsonElementTypeConverterFactory implements ElementTypeConverterFactory {

  @Override
  public @NotNull ElementTypeConverter getElementTypeConverter() {
    return ElementTypeConverterKt.elementTypeConverterOf(
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getARRAY(), JsonElementTypes.ARRAY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getBOOLEAN_LITERAL(), JsonElementTypes.BOOLEAN_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getLITERAL(), JsonElementTypes.LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getNULL_LITERAL(), JsonElementTypes.NULL_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getNUMBER_LITERAL(), JsonElementTypes.NUMBER_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getOBJECT(), JsonElementTypes.OBJECT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getPROPERTY(), JsonElementTypes.PROPERTY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getREFERENCE_EXPRESSION(), JsonElementTypes.REFERENCE_EXPRESSION),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getSTRING_LITERAL(), JsonElementTypes.STRING_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getVALUE(), JsonElementTypes.VALUE),

      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getL_CURLY(), JsonElementTypes.L_CURLY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getR_CURLY(), JsonElementTypes.R_CURLY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getL_BRACKET(), JsonElementTypes.L_BRACKET),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getR_BRACKET(), JsonElementTypes.R_BRACKET),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getCOMMA(), JsonElementTypes.COMMA),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getCOLON(), JsonElementTypes.COLON),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getLINE_COMMENT(), JsonElementTypes.LINE_COMMENT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getBLOCK_COMMENT(), JsonElementTypes.BLOCK_COMMENT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getDOUBLE_QUOTED_STRING(), JsonElementTypes.DOUBLE_QUOTED_STRING),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getSINGLE_QUOTED_STRING(), JsonElementTypes.SINGLE_QUOTED_STRING),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getNUMBER(), JsonElementTypes.NUMBER),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getTRUE(), JsonElementTypes.TRUE),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getFALSE(), JsonElementTypes.FALSE),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getNULL(), JsonElementTypes.NULL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.INSTANCE.getIDENTIFIER(), JsonElementTypes.IDENTIFIER)
    );
  }
}
