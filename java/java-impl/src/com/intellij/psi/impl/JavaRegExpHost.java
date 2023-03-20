// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.util.ObjectUtils;
import org.intellij.lang.regexp.AsciiUtil;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.UnicodeCharacterNames;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;


public class JavaRegExpHost implements RegExpLanguageHost {

  protected static final EnumSet<RegExpGroup.Type> SUPPORTED_NAMED_GROUP_TYPES = EnumSet.of(RegExpGroup.Type.NAMED_GROUP);
  private final DefaultRegExpPropertiesProvider myPropertiesProvider;

  private static final int myNumberOfGeneralCategoryProperties = 58;
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
    /* end of general category properties */
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
  };

  public JavaRegExpHost() {
    myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Override
  public boolean supportsInlineOptionFlag(char flag, PsiElement context) {
    return switch (flag) {
      case 'i' -> true; // case-insensitive matching
      case 'd' -> true; // Unix lines mode
      case 'm' -> true; // multiline mode
      case 's' -> true; // dotall mode
      case 'u' -> true; // Unicode-aware case folding
      case 'x' -> true; // whitespace and comments in pattern
      case 'U' -> // Enables the Unicode version of Predefined character classes and POSIX character classes
        hasAtLeastJdkVersion(context, JavaSdkVersion.JDK_1_7);
      default -> false;
    };
  }

  @Override
  public boolean characterNeedsEscaping(char c, boolean isInClass) {
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
    return switch (boundary.getType()) {
      case UNICODE_EXTENDED_GRAPHEME -> hasAtLeastJdkVersion(boundary, JavaSdkVersion.JDK_1_9);
      case RESET_MATCH -> false;
      case LINE_START, LINE_END, WORD, NON_WORD, BEGIN, END, END_NO_LINE_TERM, PREVIOUS_MATCH -> true;
    };
  }

  @Override
  public boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
    return switch (simpleClass.getKind()) {
      case UNICODE_LINEBREAK, HORIZONTAL_SPACE, NON_HORIZONTAL_SPACE, NON_VERTICAL_SPACE ->
        hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_8);
      case VERTICAL_SPACE ->
        // is vertical tab before jdk 1.8
        true;
      case UNICODE_GRAPHEME -> hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_9);
      case XML_NAME_START, NON_XML_NAME_START, XML_NAME_PART, NON_XML_NAME_PART -> false;
      default -> true;
    };
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
  public boolean isValidPropertyName(@NotNull String name) {
    return isScriptProperty(name) || isBlockProperty(name) || isCategoryProperty(name);
  }

  private static boolean isScriptProperty(@NotNull String propertyName) {
    return "script".equalsIgnoreCase(propertyName) || "sc".equalsIgnoreCase(propertyName);
  }

  private static boolean isBlockProperty(@NotNull String propertyName) {
    return "block".equalsIgnoreCase(propertyName) || "blk".equalsIgnoreCase(propertyName);
  }

  private static boolean isCategoryProperty(@NotNull String propertyName) {
    return "general_category".equalsIgnoreCase(propertyName) || "gc".equalsIgnoreCase(propertyName);
  }

  @Override
  public boolean isValidPropertyValue(@NotNull String propertyName, @NotNull String value) {
    if (isScriptProperty(propertyName)) {
      return isValidUnicodeScript(value);
    }
    else if (isBlockProperty(propertyName)) {
      return isValidUnicodeBlock(value);
    }
    else if (isCategoryProperty(propertyName)) {
      return isValidGeneralCategory(value);
    }
    return false;
  }

  public boolean isValidGeneralCategory(String value) {
    for (int i = 0; i < myNumberOfGeneralCategoryProperties; i++) {
      if (value.equals(myPropertyNames[i][0])) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isValidCategory(@NotNull String category) {
    if (category.startsWith("In")) {
      return isValidUnicodeBlock(category.substring(2));
    }
    if (category.startsWith("Is")) {
      category = category.substring(2);
      if (isValidProperty(category)) return true;

      // Unicode properties and scripts available since JDK 1.7
      category = StringUtil.toUpperCase(category);
      return switch (category) { // see java.util.regex.UnicodeProp
        // 4 aliases
        case "WHITESPACE", "HEXDIGIT", "NONCHARACTERCODEPOINT", "JOINCONTROL" -> true;
        case "ALPHABETIC", "LETTER", "IDEOGRAPHIC", "LOWERCASE", "UPPERCASE", "TITLECASE",
          "WHITE_SPACE", "CONTROL", "PUNCTUATION", "HEX_DIGIT", "ASSIGNED", "NONCHARACTER_CODE_POINT",
          "DIGIT", "ALNUM", "BLANK", "GRAPH", "PRINT", "WORD", "JOIN_CONTROL" -> true;
        default -> isValidUnicodeScript(category);
      };
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

  private static boolean isValidUnicodeBlock(@NotNull String category) {
    try {
      return Character.UnicodeBlock.forName(category) != null;
    }
    catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean isValidUnicodeScript(@NotNull String category) {
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
      return Integer.valueOf(number.getUnescapedText());
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public String[] @NotNull [] getAllKnownProperties() {
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
    return null;
  }

  @Override
  public String[] @NotNull [] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }

  @Override
  public boolean belongsToConditionalExpression(@NotNull PsiElement psiElement) {
    PsiElement parentElement = psiElement.getParent();
    if (parentElement instanceof PsiConditionalExpression) return true;

    PsiPolyadicExpression parentExpr = ObjectUtils.tryCast(parentElement, PsiPolyadicExpression.class);
    return parentExpr != null && parentExpr.getParent() instanceof PsiConditionalExpression;
  }
}
