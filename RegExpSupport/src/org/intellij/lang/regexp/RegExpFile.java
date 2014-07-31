/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class RegExpFile extends PsiFileBase {

  public RegExpFile(FileViewProvider viewProvider, final Language language) {
    super(viewProvider, language);
  }

  @NotNull
  public FileType getFileType() {
    return RegExpFileType.INSTANCE;
  }

  /**
   * @return Regexp groups this file has
   */
  @NotNull
  public Collection<RegExpGroup> getGroups() {
    return PsiTreeUtil.findChildrenOfType(this, RegExpGroup.class);
  }
}
