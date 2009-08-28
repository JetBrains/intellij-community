package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;

/**
 * @author yole
 */
public interface PreFormatProcessor {
  ExtensionPointName<PreFormatProcessor> EP_NAME = ExtensionPointName.create("com.intellij.preFormatProcessor");

  TextRange process(ASTNode element, TextRange range);
}
