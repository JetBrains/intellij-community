/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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
   * @param project the project containing the content.  
   * @return true if the content is valid, false if not
   */

  public boolean isParsable(CharSequence buffer, Language fileLanguage, final Project project) {
    return false;
  }

  /**
   * The same as {@link this#isParsable(CharSequence, com.intellij.lang.Language, com.intellij.openapi.project.Project)} 
   * but with parent ASTNode of the old node.
   * 
   * Override this method only if you really understand what are doing.
   * In other cases override {@link this#isParsable(CharSequence, com.intellij.lang.Language, com.intellij.openapi.project.Project)}
   * 
   * Known valid use-case:
   *  Indent-based languages. You should know about parent indent in order to decide if block is reparsable with given text.
   *  Because if indent of some line became equals to parent indent then the block should have another parent or block is not block anymore.
   *  So it cannot be reparsed and whole file or parent block should be reparsed.
   * 
   * @param parent parent node of old (or collapsed) reparsable node.
   * @param buffer the content to parse.
   * @param fileLanguage language of the file
   * @param project the project containing the content.  
   * @return true if the content is valid, false if not
   */
  public boolean isParsable(@Nullable ASTNode parent, CharSequence buffer, Language fileLanguage, final Project project) {
    return isParsable(buffer, fileLanguage, project);
  }
}
