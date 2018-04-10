// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class RegExpFileElementType extends IFileElementType {
  RegExpFileElementType() {
    super("REGEXP_FILE", RegExpLanguage.INSTANCE);
  }

  @Override
  public PsiBuilder parseLight(ASTNode chameleon) {
    PsiElement psi = chameleon.getPsi();
    Project project = psi.getProject();
    Language languageForParser = getLanguageForParser(psi);
    RegExpParserDefinition definition = (RegExpParserDefinition)LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser);
    EnumSet<RegExpCapability> capabilities = setupCapabilities(psi, EnumSet.copyOf(definition.getDefaultCapabilities()));
    RegExpLexer lexer = definition.createLexer(project, capabilities);
    PsiParser parser = definition.createParser(project, capabilities);
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, languageForParser, chameleon.getChars());
    ((LightPsiParser)parser).parseLight(this, builder);
    return builder;
  }

  @NotNull
  private static EnumSet<RegExpCapability> setupCapabilities(PsiElement psi, @NotNull EnumSet<RegExpCapability> capabilities) {
    PsiElement host = InjectedLanguageUtil.findInjectionHost(psi);
    if (host == null && !(psi instanceof PsiFile)) host = psi.getParent();
    Language language = host == null ? null : host.getLanguage();
    RegExpCapabilitiesProvider provider = language == null ? null : RegExpCapabilitiesProvider.EP.forLanguage(language);
    return provider == null ? capabilities : EnumSet.copyOf(provider.setup(host, capabilities));
  }

  @Override
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement psi) {
    return parseLight(chameleon).getTreeBuilt().getFirstChildNode();
  }
}
