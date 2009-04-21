/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class LanguageImportStatements extends LanguageExtension<ImportOptimizer> {
  public static final LanguageImportStatements INSTANCE = new LanguageImportStatements();

  private LanguageImportStatements() {
    super("com.intellij.lang.importOptimizer");
  }

  @Nullable
  public ImportOptimizer forFile(PsiFile file) {
    ImportOptimizer optimizer = forLanguage(file.getLanguage());
    return optimizer != null && optimizer.supports(file) ? optimizer : null;
  }
}