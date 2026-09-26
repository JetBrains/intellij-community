// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters;

import com.intellij.ide.starters.shared.TextValidationFunction;
import com.intellij.ide.starters.shared.ValidationFunctions;
import com.intellij.openapi.util.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ValidationFunctionsTest {

  private static final String SHOULD_BE_VALID = "should be valid";

  private static final Pair<TextValidationFunction, String> VERSION_FORMAT =
    new Pair<>(ValidationFunctions.CHECK_SIMPLE_NAME_FORMAT, "version format");
  private static final Pair<TextValidationFunction, String> NO_RESERVED_WORDS =
    new Pair<>(ValidationFunctions.CHECK_NO_RESERVED_WORDS, "no reserved words");
  private static final Pair<TextValidationFunction, String> GROUP_VALIDATOR =
    new Pair<>(ValidationFunctions.CHECK_GROUP_FORMAT, "group format");
  private static final Pair<TextValidationFunction, String> ARTIFACT_SIMPLE_VALIDATOR =
    new Pair<>(ValidationFunctions.CHECK_ARTIFACT_SIMPLE_FORMAT, "artifact simple format");
  private static final Pair<TextValidationFunction, String> ARTIFACT_WEB_VALIDATOR =
    new Pair<>(ValidationFunctions.CHECK_ARTIFACT_FORMAT_FOR_WEB, "artifact web format");

  @Parameter
  public Pair<TextValidationFunction, String> validatorWithId;

  @Parameter(1)
  public String inputString;

  @Parameter(2)
  public boolean shouldPassValidation;

  @Parameter(3)
  public String comment;

  @Parameters
  @SuppressWarnings("SpellCheckingInspection")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {VERSION_FORMAT, "aAbBcC xXyYzZ-._", true, "all allowed symbols"},
      {VERSION_FORMAT, "aaa * aaa", false, "*"},
      {VERSION_FORMAT, "aaa , aaa", false, ","},
      {VERSION_FORMAT, "aaa < aaa", false, "<"},
      {VERSION_FORMAT, "aaa & aaa", false, "&"},
      {VERSION_FORMAT, "aaa = aaa", false, "="},

      {NO_RESERVED_WORDS, "e", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "example", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "com.example", true, SHOULD_BE_VALID},

      {NO_RESERVED_WORDS, "com", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "com10", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "com9", false, "com9"},
      {NO_RESERVED_WORDS, "com1", false, "com1"},

      {NO_RESERVED_WORDS, "lpt", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "lpt10", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "lpt9", false, "lpt9"},
      {NO_RESERVED_WORDS, "lpt1", false, "lpt1"},

      {NO_RESERVED_WORDS, "con", false, "con"},
      {NO_RESERVED_WORDS, "prn", false, "prn"},
      {NO_RESERVED_WORDS, "aux", false, "aux"},
      {NO_RESERVED_WORDS, "nul", false, "nul"},

      {NO_RESERVED_WORDS, ".com1", false, "com1"},
      {NO_RESERVED_WORDS, ".com1.", false, "com1"},
      {NO_RESERVED_WORDS, ".com1&.", true, SHOULD_BE_VALID},
      {NO_RESERVED_WORDS, "com1.", false, "com1"},
      {NO_RESERVED_WORDS, " com1", false, "com1"},
      {NO_RESERVED_WORDS, " com1 ", false, "com1"},
      {NO_RESERVED_WORDS, "com1 ", false, "com1"},
      {NO_RESERVED_WORDS, "com1.allowedword", false, "in the beginning"},
      {NO_RESERVED_WORDS, "allowedword.com1", false, "in the end"},
      {NO_RESERVED_WORDS, "allowedword.com1.allowedword", false, "in the middle"},

      {NO_RESERVED_WORDS, "Com1", false, "com1 capitalized"},
      {NO_RESERVED_WORDS, "coM1", false, "com1 capitalized"},
      {NO_RESERVED_WORDS, "COM1", false, "com1 capitalized"},

      {GROUP_VALIDATOR, "e", true, SHOULD_BE_VALID},
      {GROUP_VALIDATOR, "example", true, SHOULD_BE_VALID},
      {GROUP_VALIDATOR, "com.example", true, SHOULD_BE_VALID},
      {GROUP_VALIDATOR, "aAzZ09_.aAzZ09_.aAzZ09_", true, "all allowed symbols"},
      {GROUP_VALIDATOR, "looooooooooooooooooooooooooooooooooong.grouuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuup", true, SHOULD_BE_VALID},
      {GROUP_VALIDATOR, "com.example ", false, "whitespace in the end"},
      {GROUP_VALIDATOR, " com.example", false, "whitespace in the beginning"},
      {GROUP_VALIDATOR, "com..example ", false, ".."},
      {GROUP_VALIDATOR, "1com.example ", false, "digit at start of the first part"},
      {GROUP_VALIDATOR, "com.1example ", false, "digit at start of the second part"},
      {GROUP_VALIDATOR, "1com.1example ", false, "digits at start of second parts"},
      {GROUP_VALIDATOR, "Aaaa aaaA", false, "forbidden symbol ' '"},
      {GROUP_VALIDATOR, "Aaaa*aaaA", false, "forbidden symbol '*'"},
      {GROUP_VALIDATOR, "Aaaa&aaaA", false, "forbidden symbol '&'"},
      {GROUP_VALIDATOR, "Aaaa%aaaA", false, "forbidden symbol '%'"},

      {ARTIFACT_SIMPLE_VALIDATOR, "e", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "E", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "example", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "Example", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "EXAMPLE", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "_example", true, SHOULD_BE_VALID},
      {ARTIFACT_SIMPLE_VALIDATOR, "azAZ09-_", true, "all allowed symbols"},
      {ARTIFACT_SIMPLE_VALIDATOR, "example.", false, "dot at end"},
      {ARTIFACT_SIMPLE_VALIDATOR, "example.example", false, "dot at center"},
      {ARTIFACT_SIMPLE_VALIDATOR, ".example", false, "dot at start"},
      {ARTIFACT_SIMPLE_VALIDATOR, "-example", false, "'-' at start"},
      {ARTIFACT_SIMPLE_VALIDATOR, "Aaaa aaaA", false, "forbidden symbol ' '"},
      {ARTIFACT_SIMPLE_VALIDATOR, "Aaaa*aaaA", false, "forbidden symbol '*'"},
      {ARTIFACT_SIMPLE_VALIDATOR, "Aaaa&aaaA", false, "forbidden symbol '&'"},
      {ARTIFACT_SIMPLE_VALIDATOR, "Aaaa%aaaA", false, "forbidden symbol '%'"},

      {ARTIFACT_WEB_VALIDATOR, "e", true, SHOULD_BE_VALID},
      {ARTIFACT_WEB_VALIDATOR, "example", true, SHOULD_BE_VALID},
      {ARTIFACT_WEB_VALIDATOR, "example.", true, SHOULD_BE_VALID},
      {ARTIFACT_WEB_VALIDATOR, "example.example", true, SHOULD_BE_VALID},
      {ARTIFACT_WEB_VALIDATOR, "az09-._", true, "all allowed symbols"},
      {ARTIFACT_WEB_VALIDATOR, "Example", false, "uppercase at start"},
      {ARTIFACT_WEB_VALIDATOR, "examplE", false, "uppercase"},
      {ARTIFACT_WEB_VALIDATOR, "1example", false, "digit at start"},
      {ARTIFACT_WEB_VALIDATOR, ".example", false, "dot at start"},
      {ARTIFACT_WEB_VALIDATOR, "-example", false, "'-' at start"},
      {ARTIFACT_WEB_VALIDATOR, "_example", false, "'_' at start"},
      {ARTIFACT_WEB_VALIDATOR, "Aaaa aaaA", false, "forbidden symbol ' '"},
      {ARTIFACT_WEB_VALIDATOR, "Aaaa*aaaA", false, "forbidden symbol '*'"},
      {ARTIFACT_WEB_VALIDATOR, "Aaaa&aaaA", false, "forbidden symbol '&'"},
      {ARTIFACT_WEB_VALIDATOR, "Aaaa%aaaA", false, "forbidden symbol '%'"},
    });
  }

  @Test
  public void test() {
    doTest(validatorWithId, inputString, shouldPassValidation, comment);
  }

  public static void doTest(Pair<TextValidationFunction, String> validatorWithId,
                            String inputString,
                            boolean shouldPassValidation, String comment) {
    String validationErrorMessage = validatorWithId.first.checkText(inputString);
    if (shouldPassValidation != (validationErrorMessage == null)) {
      StringBuilder testReport = new StringBuilder();
      testReport.append("Input string: \"").append(inputString).append("\"")
        .append("\nValidator: ").append(validatorWithId.second)
        .append("\nExpected validation result: ").append(shouldPassValidation)
        .append("\nCurrent validation result: ").append(validationErrorMessage == null);
      if (validationErrorMessage != null) {
        testReport.append("\nValidation error message: ").append(validationErrorMessage);
      }
      if (comment != null) {
        testReport.append("\nTest comment: ").append(comment);
      }
      fail(testReport.toString());
    }
  }
}