/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.AsciiUtil;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.UnicodeCharacterNames;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;

/**
 * @author yole
 */
public class JavaRegExpHost implements RegExpLanguageHost {

  protected static final EnumSet<RegExpGroup.Type> SUPPORTED_NAMED_GROUP_TYPES = EnumSet.of(RegExpGroup.Type.NAMED_GROUP);
  private final DefaultRegExpPropertiesProvider myPropertiesProvider;

  private final String[][] myPropertyNames = {
    {"Cn", "Unassigned"},
    {"Lu", "Uppercase letter"},
    {"Ll", "Lowercase letter"},
    {"Lt", "Titlecase letter"},
    {"Lm", "Modifier letter"},
    {"Lo", "Other letter"},
    {"Mn", "Non spacing mark"},
    {"Me", "Enclosing mark"},
    {"Mc", "Combining spacing mark"},
    {"Nd", "Decimal digit number"},
    {"Nl", "Letter number"},
    {"No", "Other number"},
    {"Zs", "Space separator"},
    {"Zl", "Line separator"},
    {"Zp", "Paragraph separator"},
    {"Cc", "Control"},
    {"Cf", "Format"},
    {"Co", "Private use"},
    {"Cs", "Surrogate"},
    {"Pd", "Dash punctuation"},
    {"Ps", "Start punctuation"},
    {"Pe", "End punctuation"},
    {"Pc", "Connector punctuation"},
    {"Po", "Other punctuation"},
    {"Sm", "Math symbol"},
    {"Sc", "Currency symbol"},
    {"Sk", "Modifier symbol"},
    {"So", "Other symbol"},
    {"Pi", "Initial quote punctuation"},
    {"Pf", "Final quote punctuation"},
    {"L", "Letter"},
    {"M", "Mark"},
    {"N", "Number"},
    {"Z", "Separator"},
    {"C", "Control"},
    {"P", "Punctuation"},
    {"S", "Symbol"},
    {"LC", "Letter"},
    {"LD", "Letter or digit"},
    {"L1", "Latin-1"},
    {"all", "All"},
    {"ASCII", "Ascii"},
    {"Alnum", "Alphanumeric characters"},
    {"Alpha", "Alphabetic characters"},
    {"Blank", "Space and tab characters"},
    {"Cntrl", "Control characters"},
    {"Digit", "Numeric characters"},
    {"Graph", "Printable and visible"},
    {"Lower", "Lowercase Alphabetic"},
    {"Print", "Printable characters"},
    {"Punct", "Punctuation characters"},
    {"Space", "Space characters"},
    {"Upper", "Uppercase alphabetic"},
    {"XDigit", "Hexadecimal digits"},
    {"javaLowerCase", },
    {"javaUpperCase", },
    {"javaTitleCase", },
    {"javaAlphabetic", },
    {"javaIdeographic", },
    {"javaDigit", },
    {"javaDefined", },
    {"javaLetter", },
    {"javaLetterOrDigit", },
    {"javaJavaIdentifierStart", },
    {"javaJavaIdentifierPart", },
    {"javaUnicodeIdentifierStart", },
    {"javaUnicodeIdentifierPart", },
    {"javaIdentifierIgnorable", },
    {"javaSpaceChar", },
    {"javaWhitespace", },
    {"javaISOControl", },
    {"javaMirrored", },
  };

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
    return false;
  }

  @Override
  public boolean supportsNamedCharacters(RegExpNamedCharacter namedCharacter) {
    return hasAtLeastJdkVersion(namedCharacter, JavaSdkVersion.JDK_1_9);
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
    return group.getType() == RegExpGroup.Type.NAMED_GROUP && hasAtLeastJdkVersion(group, JavaSdkVersion.JDK_1_7);
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    return ref.isNamedGroupRef() && hasAtLeastJdkVersion(ref, JavaSdkVersion.JDK_1_7);
  }

  @NotNull
  @Override
  public EnumSet<RegExpGroup.Type> getSupportedNamedGroupTypes(RegExpElement context) {
    if (!hasAtLeastJdkVersion(context, JavaSdkVersion.JDK_1_7)) {
      return EMPTY_NAMED_GROUP_TYPES;
    }
    return SUPPORTED_NAMED_GROUP_TYPES;
  }

  @Override
  public boolean isValidGroupName(String name, @NotNull RegExpGroup group) {
    for (int i = 0, length = name.length(); i < length; i++) {
      final char c = name.charAt(i);
      if (!AsciiUtil.isLetterOrDigit(c)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    return regExpChar.getUnescapedText().charAt(1) == 'x' && hasAtLeastJdkVersion(regExpChar, JavaSdkVersion.JDK_1_7);
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
    if (category.startsWith("In")) {
      return isValidUnicodeBlock(category);
    }
    if (category.startsWith("Is")) {
      category = category.substring(2);
      if (isValidProperty(category)) return true;

      // Unicode properties and scripts available since JDK 1.7
      category = category.toUpperCase(Locale.ENGLISH);
      switch (category) { // see java.util.regex.UnicodeProp
        // 4 aliases
        case "WHITESPACE":
        case "HEXDIGIT":
        case "NONCHARACTERCODEPOINT":
        case "JOINCONTROL":

        case "ALPHABETIC":
        case "LETTER":
        case "IDEOGRAPHIC":
        case "LOWERCASE":
        case "UPPERCASE":
        case "TITLECASE":
        case "WHITE_SPACE":
        case "CONTROL":
        case "PUNCTUATION":
        case "HEX_DIGIT":
        case "ASSIGNED":
        case "NONCHARACTER_CODE_POINT":
        case "DIGIT":
        case "ALNUM":
        case "BLANK":
        case "GRAPH":
        case "PRINT":
        case "WORD":
        case "JOIN_CONTROL":
          return true;
      }
      return isValidUnicodeScript(category);
    }
    return isValidProperty(category);
  }

  private boolean isValidProperty(@NotNull String category) {
    for (String[] name : myPropertyNames) {
      if (name[0].equals(category)) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidUnicodeBlock(@NotNull String category) {
    try {
      return Character.UnicodeBlock.forName(category.substring(2)) != null;
    }
    catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean isValidUnicodeScript(@NotNull String category) {
    try {
      return Character.UnicodeScript.forName(category) != null;
    }
    catch (IllegalArgumentException ignore) {
      return false;
    }
  }

  @Override
  public boolean isValidNamedCharacter(RegExpNamedCharacter namedCharacter) {
    return UnicodeCharacterNames.getCodePoint(namedCharacter.getName()) >= 0;
  }

  @Override
  public Lookbehind supportsLookbehind(@NotNull RegExpGroup lookbehindGroup) {
    return Lookbehind.FINITE_REPETITION;
  }

  @Override
  public Integer getQuantifierValue(@NotNull RegExpNumber number) {
    try {
      return Integer.valueOf(number.getText());
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @NotNull
  @Override
  public String[][] getAllKnownProperties() {
    return myPropertyNames;
  }

  @Nullable
  @Override
  public String getPropertyDescription(@Nullable String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return null;
    }
    for (String[] stringArray : myPropertyNames) {
      if (stringArray[0].equals(name)) {
        return stringArray.length > 1 ? stringArray[1] : stringArray[0];
      }
    }
    return null;  }

  @NotNull
  @Override
  public String[][] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }
}
