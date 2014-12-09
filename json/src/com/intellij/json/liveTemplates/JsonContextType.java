package com.intellij.json.liveTemplates;

import com.intellij.codeInsight.template.FileTypeBasedContextType;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author Konstantin.Ulitin
 */
public class JsonContextType extends FileTypeBasedContextType {
  protected JsonContextType() {
    super("JSON", JsonBundle.message("json.template.context.type"), JsonFileType.INSTANCE);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    if (!super.isInContext(file, offset)) {
      return false;
    }
    final ElementPattern<PsiElement> illegalPattern = or(psiElement().inside(JsonStringLiteral.class),
                                                         psiElement().inside(psiElement(JsonValue.class)
                                                                               .with(new PatternCondition<PsiElement>("insidePropertyKey") {
                                                                                 @Override
                                                                                 public boolean accepts(@NotNull PsiElement element,
                                                                                                        ProcessingContext context) {
                                                                                   return JsonPsiUtil.isPropertyKey(element);
                                                                                 }
                                                                               })));
    return !illegalPattern.accepts(file.findElementAt(offset));
  }
}
