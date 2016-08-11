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

  private final String[][] myCharacterClasses = {
    {"d", "Digit: [0-9]"},
    {"D", "Nondigit: [^0-9]"},
    {"h", "Horizontal whitespace character: [ \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000]"},
    {"H", "Non-horizontal whitespace character: [^\\h]"},
    {"s", "Whitespace: [ \\t\\n\\x0B\\f\\r]"},
    {"S", "Non-whitespace: [^\\s]"},
    {"v", "Vertical whitespace character: [\\n\\x0B\\f\\r\\x85\\u2028\\u2029]"},
    {"V", "Non-vertical whitespace character: [^\\v]"},
    {"w", "Word character: [a-zA-Z_0-9]"},
    {"W", "Nonword character: [^\\w]"},
    {"b", "Word boundary"},
    {"b{g}", "Unicode extended grapheme cluster boundary"},
    {"B", "Non-word boundary"},
    {"A", "Beginning of the input"},
    {"G", "End of the previous match"},
    {"Z", "End of the input but for the final terminator, if any"},
    {"z", "End of input"},
    {"Q", "Nothing, but quotes all characters until \\E"},
    {"E", "Nothing, but ends quoting started by \\Q"},
    {"t", "Tab character ('\\u0009')"},
    {"n", "Newline (line feed) character ('\\u000A')"},
    {"r", "Carriage-return character ('\\u000D')"},
    {"f", "Form-feed character ('\\u000C')"},
    {"a", "Alert (bell) character ('\\u0007')"},
    {"e", "Escape character ('\\u001B')"},
    {"R", "Unicode line ending: \\u000D\\u000A|[\\u000A\\u000B\\u000C\\u000D\\u0085\\u2028\\u2029]"},
    {"X", "Unicode extended grapheme cluster"}
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
