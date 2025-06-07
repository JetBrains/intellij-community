// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

/**
 * OBSOLESCENCE NOTE:
 * Use {@link com.intellij.psi.stubs.LightLanguageStubDefinition} instead
 */
@ApiStatus.Obsolete
public class ILightStubFileElementType<T extends PsiFileStub> extends IStubFileElementType<T> {
  public ILightStubFileElementType(Language language) {
    super(language);
  }

  public ILightStubFileElementType(@NonNls String debugName, Language language) {
    super(debugName, language);
  }

  @Override
  public LightStubBuilder getBuilder() {
    return new LightStubBuilder();
  }

  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(ASTNode chameleon) {
    PsiElement psi = chameleon.getPsi();
    assert psi != null : "Bad chameleon: " + chameleon;

    Project project = psi.getProject();
    PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    PsiBuilder builder = factory.createBuilder(project, chameleon);
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
    assert parserDefinition != null : this;
    PsiParser parser = parserDefinition.createParser(project);
    if (parser instanceof LightPsiParser) {
      ((LightPsiParser)parser).parseLight(this, builder);
    }
    else {
      parser.parse(this, builder);
    }
    return builder.getLightTree();
  }
}