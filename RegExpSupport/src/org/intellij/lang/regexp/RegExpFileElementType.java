// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ParsingDiagnostics;
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
  protected ASTNode doParseContents(@NotNull ASTNode chameleon, @NotNull PsiElement outerPsi) {
    PsiElement psi = chameleon.getPsi();
    Project project = psi.getProject();
    Language languageForParser = getLanguageForParser(psi);
    RegExpParserDefinition definition = (RegExpParserDefinition)LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser);
    EnumSet<RegExpCapability> capabilities = setupCapabilities(psi, EnumSet.copyOf(definition.getDefaultCapabilities()));
    RegExpLexer lexer = definition.createLexer(project, capabilities);
    PsiParser parser = definition.createParser(project, capabilities);
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, languageForParser, chameleon.getChars());
    var startTime = System.nanoTime();
    ((LightPsiParser)parser).parseLight(this, builder);
    var result = builder.getTreeBuilt().getFirstChildNode();
    ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
    return result;
  }

  private static @NotNull EnumSet<RegExpCapability> setupCapabilities(PsiElement psi, @NotNull EnumSet<RegExpCapability> capabilities) {
    PsiElement host = InjectedLanguageUtil.findInjectionHost(psi);
    if (host == null && !(psi instanceof PsiFile)) host = psi.getParent();
    Language language = host == null ? null : host.getLanguage();
    RegExpCapabilitiesProvider provider = language == null ? null : RegExpCapabilitiesProvider.EP.forLanguage(language);
    return provider == null ? capabilities : EnumSet.copyOf(provider.setup(host, capabilities));
  }
}
