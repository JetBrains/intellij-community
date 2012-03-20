/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
   * @param fileLanguage language of the file
   * @param project the project containing the content.  @return true if the content is valid, false if not
   */

  public boolean isParsable(CharSequence buffer, Language fileLanguage, final Project project) {
    return false;
  }
}
