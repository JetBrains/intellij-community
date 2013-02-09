package org.intellij.lang.regexp;

import com.intellij.lang.LanguageExtension;

public final class RegExpPropertiesProviders extends LanguageExtension<RegExpPropertiesProvider> {
  private static final RegExpPropertiesProviders INSTANCE = new RegExpPropertiesProviders();

  public static RegExpPropertiesProviders getInstance() {
    return INSTANCE;
  }

  public RegExpPropertiesProviders() {
    super("com.intellij.regExpPropertiesProvider", new RegExpPropertyNameProvider());
  }
}
