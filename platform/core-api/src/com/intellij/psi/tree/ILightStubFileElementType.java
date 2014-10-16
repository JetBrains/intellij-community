/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;


public class ILightStubFileElementType<T extends PsiFileStub> extends IStubFileElementType<T> {
  public ILightStubFileElementType(final Language language) {
    super(language);
  }

  public ILightStubFileElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }

  @Override
  public LightStubBuilder getBuilder() {
    return new LightStubBuilder();
  }

  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(final ASTNode chameleon) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : "Bad chameleon: " + chameleon;

    final Project project = psi.getProject();
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final PsiBuilder builder = factory.createBuilder(project, chameleon);
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
    assert parserDefinition != null : this;
    final PsiParser parser = parserDefinition.createParser(project);
    if (parser instanceof LightPsiParser) {
      ((LightPsiParser)parser).parseLight(this, builder);
    }
    else {
      parser.parse(this, builder);
    }
    return builder.getLightTree();
  }
}