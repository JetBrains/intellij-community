/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultRegExpPropertiesProvider {
  private static final DefaultRegExpPropertiesProvider INSTANCE = new DefaultRegExpPropertiesProvider();

  public static DefaultRegExpPropertiesProvider getInstance() {
    return INSTANCE;
  }

  private final String[][] myPropertyNames = {
          { "Cn", "UNASSIGNED" },
          { "Lu", "UPPERCASE_LETTER" },
          { "Ll", "LOWERCASE_LETTER" },
          { "Lt", "TITLECASE_LETTER" },
          { "Lm", "MODIFIER_LETTER" },
          { "Lo", "OTHER_LETTER" },
          { "Mn", "NON_SPACING_MARK" },
          { "Me", "ENCLOSING_MARK" },
          { "Mc", "COMBINING_SPACING_MARK" },
          { "Nd", "DECIMAL_DIGIT_NUMBER" },
          { "Nl", "LETTER_NUMBER" },
          { "No", "OTHER_NUMBER" },
          { "Zs", "SPACE_SEPARATOR" },
          { "Zl", "LINE_SEPARATOR" },
          { "Zp", "PARAGRAPH_SEPARATOR" },
          { "Cc", "CNTRL" },
          { "Cf", "FORMAT" },
          { "Co", "PRIVATE USE" },
          { "Cs", "SURROGATE" },
          { "Pd", "DASH_PUNCTUATION" },
          { "Ps", "START_PUNCTUATION" },
          { "Pe", "END_PUNCTUATION" },
          { "Pc", "CONNECTOR_PUNCTUATION" },
          { "Po", "OTHER_PUNCTUATION" },
          { "Sm", "MATH_SYMBOL" },
          { "Sc", "CURRENCY_SYMBOL" },
          { "Sk", "MODIFIER_SYMBOL" },
          { "So", "OTHER_SYMBOL" },
          { "Pi", "INITIAL_QUOTE_PUNCTUATION" },
          { "Pf", "FINAL_QUOTE_PUNCTUATION" },
          { "L", "LETTER" },
          { "M", "MARK" },
          { "N", "NUMBER" },
          { "Z", "SEPARATOR" },
          { "C", "CONTROL" },
          { "P", "PUNCTUATION" },
          { "S", "SYMBOL" },
          { "LC", "LETTER" },
          { "LD", "LETTER_OR_DIGIT" },
          { "L1", "Latin-1" },
          { "all", "ALL" },
          { "ASCII", "ASCII" },
          { "Alnum", "Alphanumeric characters" },
          { "Alpha", "Alphabetic characters" },
          { "Blank", "Space and tab characters" },
          { "Cntrl", "Control characters" },
          { "Digit", "Numeric characters" },
          { "Graph", "printable and visible" },
          { "Lower", "Lower-case alphabetic" },
          { "Print", "Printable characters" },
          { "Punct", "Punctuation characters" },
          { "Space", "Space characters" },
          { "Upper", "Upper-case alphabetic" },
          { "XDigit", "hexadecimal digits" },
          { "javaLowerCase", },
          { "javaUpperCase", },
          { "javaTitleCase", },
          { "javaAlphabetic", },
          { "javaIdeographic", },
          { "javaDigit", },
          { "javaDefined", },
          { "javaLetter", },
          { "javaLetterOrDigit", },
          { "javaJavaIdentifierStart", },
          { "javaJavaIdentifierPart", },
          { "javaUnicodeIdentifierStart", },
          { "javaUnicodeIdentifierPart", },
          { "javaIdentifierIgnorable", },
          { "javaSpaceChar", },
          { "javaWhitespace", },
          { "javaISOControl", },
          { "javaMirrored", },
  };

  private final String[][] myCharacterClasses = {
    {"d", "digit: [0-9]"},
    {"D", "nondigit: [^0-9]"},
    {"h", "horizontal whitespace character: [ \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000]"},
    {"H", "non-horizontal whitespace character: [^\\h]"},
    {"s", "whitespace [ \\t\\n\\x0B\\f\\r]"},
    {"S", "non-whitespace [^\\s]"},
    {"v", "vertical whitespace character: [\\n\\x0B\\f\\r\\x85\\u2028\\u2029]"},
    {"V", "non-vertical whitespace character: [^\\v]"},
    {"w", "word character [a-zA-Z_0-9]"},
    {"W", "nonword character [^\\w]"},
    {"b", "word boundary"},
    {"B", "non-word boundary"},
    {"A", "beginning of the input"},
    {"G", "end of the previous match"},
    {"Z", "end of the input but for the final terminator, if any"},
    {"z", "end of input"},
    {"Q", "Nothing, but quotes all characters until \\E"},
    {"E", " \tNothing, but ends quoting started by \\Q"},
    {"t", "tab character ('\\u0009')"},
    {"n", "newline (line feed) character ('\\u000A')"},
    {"r", "carriage-return character ('\\u000D')"},
    {"f", "form-feed character ('\\u000C')"},
    {"a", "alert (bell) character ('\\u0007')"},
    {"e", "escape character ('\\u001B')"}
  };

  private final String[][] myPosixCharacterClasses = {
    {"alnum", "Alphanumeric characters"},
    {"alpha", "Alphabetic characters"},
    {"ascii", "ASCII characters"},
    {"blank", "Space and tab"},
    {"cntrl", "Control characters"},
    {"digit", "Digits"},
    {"graph", "Visible characters"},
    {"lower", "Lowercase letters"},
    {"print", "Visible characters and spaces"},
    {"punct", "Punctuation and symbols"},
    {"space", "All whitespace characters, including line breaks"},
    {"upper", "Uppercase letters"},
    {"word",  "Word characters (letters, numbers and underscores)"},
    {"xdigit","Hexadecimal digits"},
  };

  private DefaultRegExpPropertiesProvider() {
  }

  public boolean isValidCategory(@NotNull String category) {
    if (category.startsWith("In")) {
      try {
        return Character.UnicodeBlock.forName(category.substring(2)) != null;
      }
      catch (IllegalArgumentException e) {
        return false;
      }
    }
    category = StringUtil.trimStart(category, "Is");
    for (String[] name : myPropertyNames) {
      if (name[0].equals(category)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public String getPropertyDescription(@Nullable final String name) {
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

  @NotNull
  public String[][] getAllKnownProperties() {
    return myPropertyNames;
  }

  @NotNull
  public String[][] getKnownCharacterClasses() {
    return myCharacterClasses;
  }

  @NotNull
  public String[][] getPosixCharacterClasses() {
    return myPosixCharacterClasses;
  }
}
