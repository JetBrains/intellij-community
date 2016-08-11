/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaRegExpHost implements RegExpLanguageHost {

  private final DefaultRegExpPropertiesProvider myPropertiesProvider;

  public JavaRegExpHost() {
    myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Override
  public boolean supportsInlineOptionFlag(char flag, PsiElement context) {
    switch (flag) {
      case 'i': // case-insensitive matching
      case 'd': // Unix lines mode
      case 'm': // multiline mode
      case 's': // dotall mode
      case 'u': // Unicode-aware case folding
      case 'x': // whitespace and comments in pattern
        return true;
      case 'U': // Enables the Unicode version of Predefined character classes and POSIX character classes
        return hasAtLeastJdkVersion(context, JavaSdkVersion.JDK_1_7);
      default:
        return false;
    }
  }

  @Override
  public boolean characterNeedsEscaping(char c) {
    return c == ']' || c == '}';
  }

  @Override
  public boolean supportsPerl5EmbeddedComments() {
    return false;
  }

  @Override
  public boolean supportsPossessiveQuantifiers() {
    return true;
  }

  @Override
  public boolean supportsPythonConditionalRefs() {
    return false;
  }

  @Override
  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    return group.isNamedGroup() && hasAtLeastJdkVersion(group, JavaSdkVersion.JDK_1_7);
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    return ref.isNamedGroupRef() && hasAtLeastJdkVersion(ref, JavaSdkVersion.JDK_1_7);
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    return hasAtLeastJdkVersion(regExpChar, JavaSdkVersion.JDK_1_7);
  }

  @Override
  public boolean supportsBoundary(RegExpBoundary boundary) {
    switch (boundary.getType()) {
      case UNICODE_EXTENDED_GRAPHEME:
        return hasAtLeastJdkVersion(boundary, JavaSdkVersion.JDK_1_9);
      case LINE_START:
      case LINE_END:
      case WORD:
      case NON_WORD:
      case BEGIN:
      case END:
      case END_NO_LINE_TERM:
      case PREVIOUS_MATCH:
      default:
        return true;
    }
  }

  @Override
  public boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
    switch(simpleClass.getKind()) {
      case UNICODE_LINEBREAK:
      case HORIZONTAL_SPACE:
      case NON_HORIZONTAL_SPACE:
      case NON_VERTICAL_SPACE:
        return hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_8);
      case VERTICAL_SPACE:
        // is vertical tab before jdk 1.8
        return true;
      case UNICODE_GRAPHEME:
        return hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_9);
      case XML_NAME_START:
      case NON_XML_NAME_START:
      case XML_NAME_PART:
      case NON_XML_NAME_PART:
        return false;
      default:
        return true;
    }
  }

  @Override
  public boolean supportsLiteralBackspace(RegExpChar aChar) {
    return false;
  }

  private static boolean hasAtLeastJdkVersion(PsiElement element, JavaSdkVersion version) {
    return getJavaVersion(element).isAtLeast(version);
  }

  @NotNull
  private static JavaSdkVersion getJavaVersion(PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
        final JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        if (version != null) {
          return version;
        }
      }
    }
    return JavaSdkVersion.JDK_1_9;
  }

  @Override
  public boolean isValidCategory(@NotNull String category) {
    return myPropertiesProvider.isValidCategory(category);
  }

  @NotNull
  @Override
  public String[][] getAllKnownProperties() {
    return myPropertiesProvider.getAllKnownProperties();
  }

  @Nullable
  @Override
  public String getPropertyDescription(@Nullable String name) {
    return myPropertiesProvider.getPropertyDescription(name);
  }

  @NotNull
  @Override
  public String[][] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }
}
