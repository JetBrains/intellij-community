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
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

public class IFileElementType extends ILazyParseableElementType {
  public IFileElementType(final Language language) {
    super("FILE", language);
  }

  public IFileElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  public ASTNode parseContents(ASTNode chameleon) {
    final Project project = chameleon.getPsi().getProject();
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();

    final PsiBuilder builder = factory.createBuilder(
      project,
      chameleon,
      null, getLanguage(),
      chameleon.getChars()
    );

    final PsiParser parser = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage()).createParser(project);
    return parser.parse(this, builder).getFirstChildNode();
  }
}
