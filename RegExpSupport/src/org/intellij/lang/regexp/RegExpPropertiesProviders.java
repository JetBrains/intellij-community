package org.intellij.lang.regexp;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public final class RegExpPropertiesProviders extends LanguageExtension<RegExpPropertiesProvider> {
  private static final RegExpPropertiesProviders INSTANCE = new RegExpPropertiesProviders();

  public static RegExpPropertiesProviders getInstance() {
    return INSTANCE;
  }

  public RegExpPropertiesProviders() {
    super("com.intellij.regExpPropertiesProvider", new DefaultRegExpPropertiesProvider());
  }

  public static RegExpPropertiesProvider forNode(@NotNull final ASTNode node) {
    return getInstance().forLanguage(node.getPsi().getContainingFile().getLanguage());
  }
}
