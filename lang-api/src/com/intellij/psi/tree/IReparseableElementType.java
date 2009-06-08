/*
 * @author max
 */
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public class IReparseableElementType extends ILazyParseableElementType {
  public IReparseableElementType(@NonNls String debugName) {
    super(debugName);
  }

  public IReparseableElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  public IReparseableElementType(@NonNls String debugName, Language language, boolean register) {
    super(debugName, language, register);
  }


  /**
   * Checks if the specified character sequence can be parsed as a valid content of the
   * chameleon node.
   *
   * @param buffer  the content to parse.
   * @param project the project containing the content.
   * @return true if the content is valid, false if not
   */

  public boolean isParsable(CharSequence buffer, final Project project) {
    return false;
  }
}
