// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.openapi.util.ClassExtension;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;


public final class RegExpLanguageHosts extends ClassExtension<RegExpLanguageHost> {
  private static final RegExpLanguageHosts INSTANCE = new RegExpLanguageHosts();
  private final DefaultRegExpPropertiesProvider myDefaultProvider;

  public static RegExpLanguageHosts getInstance() {
    return INSTANCE;
  }

  private RegExpLanguageHosts() {
    super("com.intellij.regExpLanguageHost");
    myDefaultProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Contract("null -> null")
  @Nullable
  private static RegExpLanguageHost findRegExpHost(@Nullable final PsiElement element) {
    if (element == null) {
      return null;
    }
    final PsiFile file = element.getContainingFile();
    final PsiElement context = file.getContext();
    if (context instanceof RegExpLanguageHost) {
      return (RegExpLanguageHost)context;
    }
    if (context != null) {
      return INSTANCE.forClass(context.getClass());
    }
    return null;
  }

  public boolean isRedundantEscape(@NotNull final RegExpChar ch, @NotNull final String text) {
    if (text.length() <= 1) {
      return false;
    }
    final RegExpLanguageHost host = findRegExpHost(ch);
    if (host != null) {
      final char c = text.charAt(1);
      return !host.characterNeedsEscaping(c, ch.getParent() instanceof RegExpClass);
    }
    else {
      return !("\\]".equals(text) || "\\}".equals(text));
    }
  }

  public boolean supportsInlineOptionFlag(char flag, PsiElement context) {
    final RegExpLanguageHost host = findRegExpHost(context);
    return host == null || host.supportsInlineOptionFlag(flag, context);
  }

  public boolean supportsExtendedHexCharacter(@Nullable RegExpChar regExpChar) {
    final RegExpLanguageHost host = findRegExpHost(regExpChar);
    try {
      return host == null || host.supportsExtendedHexCharacter(regExpChar);
    } catch (AbstractMethodError e) {
      // supportsExtendedHexCharacter not present
      return false;
    }
  }

  public boolean supportsLiteralBackspace(@Nullable RegExpChar regExpChar) {
    final RegExpLanguageHost host = findRegExpHost(regExpChar);
    return host == null || host.supportsLiteralBackspace(regExpChar);
  }

  public boolean supportsPropertySyntax(@NotNull PsiElement context) {
    RegExpLanguageHost host = findRegExpHost(context);
    return host == null || host.supportsPropertySyntax(context);
  }

  public boolean supportsNamedGroupSyntax(@Nullable final RegExpGroup group) {
    final RegExpLanguageHost host = findRegExpHost(group);
    return host == null || host.supportsNamedGroupSyntax(group);
  }

  public boolean supportsNamedGroupRefSyntax(@Nullable final RegExpNamedGroupRef ref) {
    final RegExpLanguageHost host = findRegExpHost(ref);
    try {
      return host == null || host.supportsNamedGroupRefSyntax(ref);
    } catch (AbstractMethodError e) {
      // supportsNamedGroupRefSyntax() not present
      return false;
    }
  }

  public Collection<RegExpGroup.Type> getSupportedNamedGroupTypes(RegExpElement context) {
    final RegExpLanguageHost host = findRegExpHost(context);
    if (host == null) {
      return Collections.emptySet();
    }
    return host.getSupportedNamedGroupTypes(context);
  }

  public boolean isValidGroupName(String name, @Nullable final RegExpGroup group) {
    final RegExpLanguageHost host = findRegExpHost(group);
    return host == null || host.isValidGroupName(name, group);
  }

  public boolean isDuplicateGroupNamesAllowed(@NotNull final RegExpGroup group) {
    final RegExpLanguageHost host = findRegExpHost(group);
    return host == null || host.isDuplicateGroupNamesAllowed(group);
  }

  public boolean supportsPerl5EmbeddedComments(@Nullable final PsiComment comment) {
    final RegExpLanguageHost host = findRegExpHost(comment);
    return host == null || host.supportsPerl5EmbeddedComments();
  }

  public boolean supportsConditionals(@Nullable final RegExpConditional conditional) {
    final RegExpLanguageHost host = findRegExpHost(conditional);
    return host == null || host.supportsPythonConditionalRefs();
  }

  public boolean supportConditionalCondition(RegExpAtom condition) {
    final RegExpLanguageHost host = findRegExpHost(condition);
    return host == null || host.supportConditionalCondition(condition);
  }

  public boolean supportsPossessiveQuantifiers(@Nullable final RegExpElement context) {
    final RegExpLanguageHost host = findRegExpHost(context);
    return host == null || host.supportsPossessiveQuantifiers();
  }

  public boolean supportsBoundary(@Nullable final RegExpBoundary boundary) {
    final RegExpLanguageHost host = findRegExpHost(boundary);
    return host == null || host.supportsBoundary(boundary);
  }

  public boolean supportsSimpleClass(@Nullable final RegExpSimpleClass simpleClass) {
    final RegExpLanguageHost host = findRegExpHost(simpleClass);
    return host == null || host.supportsSimpleClass(simpleClass);
  }

  public boolean isValidCategory(@NotNull final PsiElement element, @NotNull String category) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.isValidCategory(category) : myDefaultProvider.isValidCategory(category);
  }

  public boolean isValidPropertyName(@NotNull PsiElement element, @NotNull String type) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host == null || host.isValidPropertyName(type);
  }
  
  public boolean isValidPropertyValue(@NotNull PsiElement element, @NotNull String propertyName, @NotNull String propertyValue) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host == null || host.isValidPropertyValue(propertyName, propertyValue);
  }

  public boolean supportsNamedCharacters(@NotNull final RegExpNamedCharacter namedCharacter) {
    final RegExpLanguageHost host = findRegExpHost(namedCharacter);
    return host == null || host.supportsNamedCharacters(namedCharacter);
  }

  public boolean isValidNamedCharacter(@NotNull final RegExpNamedCharacter namedCharacter) {
    final RegExpLanguageHost host = findRegExpHost(namedCharacter);
    return host == null || host.isValidNamedCharacter(namedCharacter);
  }

  public RegExpLanguageHost.Lookbehind supportsLookbehind(RegExpGroup group) {
    final RegExpLanguageHost host = findRegExpHost(group);
    if (host == null) {
      return RegExpLanguageHost.Lookbehind.FULL;
    }
    return host.supportsLookbehind(group);
  }

  @Nullable
  public Number getQuantifierValue(@NotNull RegExpNumber valueElement) {
    final RegExpLanguageHost host = findRegExpHost(valueElement);
    if (host == null) {
      return Double.valueOf(valueElement.getText());
    }
    return host.getQuantifierValue(valueElement);
  }

  public String[] @NotNull [] getAllKnownProperties(@NotNull final PsiElement element) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.getAllKnownProperties() : myDefaultProvider.getAllKnownProperties();
  }

  public String[] @NotNull [] getAllPropertyValues(@NotNull PsiElement element, @NotNull String propertyName) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.getAllPropertyValues(propertyName) : RegExpLanguageHost.EMPTY_COMPLETION_ITEMS_ARRAY;
  }

  @Nullable
  String getPropertyDescription(@NotNull final PsiElement element, @Nullable final String name) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ?  host.getPropertyDescription(name) : myDefaultProvider.getPropertyDescription(name);
  }

  String[] @NotNull [] getKnownCharacterClasses(@NotNull final PsiElement element) {
    final RegExpLanguageHost host = findRegExpHost(element);
    return host != null ? host.getKnownCharacterClasses() : myDefaultProvider.getKnownCharacterClasses();
  }

  String[][] getPosixCharacterClasses(@NotNull final PsiElement element) {
    return myDefaultProvider.getPosixCharacterClasses();
  }

  public boolean belongsToConditionalExpression(@NotNull PsiElement regexpElement, @NotNull PsiElement hostElement) {
    final RegExpLanguageHost host = findRegExpHost(regexpElement);
    return host != null && host.belongsToConditionalExpression(hostElement);
  }
}
