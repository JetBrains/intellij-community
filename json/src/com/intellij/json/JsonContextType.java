package com.intellij.json;

import com.intellij.codeInsight.template.FileTypeBasedContextType;

/**
 * @author Konstantin.Ulitin
 */
public class JsonContextType extends FileTypeBasedContextType {
  protected JsonContextType() {
    super("JSON", JsonBundle.message("json.template.context.type"), JsonFileType.INSTANCE);
  }
}
