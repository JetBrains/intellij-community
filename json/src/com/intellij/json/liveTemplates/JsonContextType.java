package com.intellij.json.liveTemplates;

import com.intellij.codeInsight.template.FileTypeBasedContextType;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public class JsonContextType extends FileTypeBasedContextType {
  protected JsonContextType() {
    super("JSON", JsonBundle.message("json.template.context.type"), JsonFileType.INSTANCE);
  }

  @Override
  public boolean isInContext(@NotNull PsiFile file, int offset) {
    return file instanceof JsonFile;
  }
}
