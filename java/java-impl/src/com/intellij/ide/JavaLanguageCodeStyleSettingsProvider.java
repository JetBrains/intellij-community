/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.application.options.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import org.jetbrains.annotations.NotNull;

/**
 * @author rvishnyakov
 */
public class JavaLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return StdLanguages.JAVA;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) return SPACING_SAMPLE;
    if (settingsType == SettingsType.INDENTS_AND_BRACES_SETTINGS) return INDENT_AND_BRACES_SAMPLE;
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) return BLANK_LINE_SAMPLE;
    if (settingsType == SettingsType.WRAPPING_SETTINGS) return WRAPPING_CODE_SAMPLE;

    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    consumer.showAllStandardOptions();
  }

  private static final String GENERAL_CODE_SAMPLE = "public class Foo {\n" +
                                                    "  public int[] X = new int[]{1, 3, 5 7, 9, 11};\n" +
                                                    "\n" +
                                                    "  public void foo(boolean a, int x, int y, int z) {\n" +
                                                    "    label1:\n" +
                                                    "    do {\n" +
                                                    "      try {\n" +
                                                    "        if (x > 0) {\n" +
                                                    "          int someVariable = a ? x : y;\n" +
                                                    "          int anotherVariable = a ? x : y;\n" +
                                                    "        }\n" +
                                                    "        else if (x < 0) {\n" +
                                                    "          int someVariable = (y + z);\n" +
                                                    "          someVariable = x = x + y;\n" +
                                                    "        }\n" +
                                                    "        else {\n" +
                                                    "          label2:\n" +
                                                    "          for (int i = 0; i < 5; i++) doSomething(i);\n" +
                                                    "        }\n" +
                                                    "        switch (a) {\n" +
                                                    "          case 0:\n" +
                                                    "            doCase0();\n" +
                                                    "            break;\n" +
                                                    "          default:\n" +
                                                    "            doDefault();\n" +
                                                    "        }\n" +
                                                    "      }\n" +
                                                    "      catch (Exception e) {\n" +
                                                    "        processException(e.getMessage(), x + y, z, a);\n" +
                                                    "      }\n" +
                                                    "      finally {\n" +
                                                    "        processFinally();\n" +
                                                    "      }\n" +
                                                    "    }\n" +
                                                    "    while (true);\n" +
                                                    "\n" +
                                                    "    if (2 < 3) return;\n" +
                                                    "    if (3 < 4) return;\n" +
                                                    "    do {\n" +
                                                    "      x++\n" +
                                                    "    }\n" +
                                                    "    while (x < 10000);\n" +
                                                    "    while (x < 50000) x++;\n" +
                                                    "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
                                                    "  }\n" +
                                                    "\n" +
                                                    "  private class InnerClass implements I1, I2 {\n" +
                                                    "    public void bar() throws E1, E2 {\n" +
                                                    "    }\n" +
                                                    "  }\n" +
                                                    "}";

  private static final String BLANK_LINE_SAMPLE = "/*\n" +
                                                       " * This is a sample file.\n" +
                                                       " */\n" +
                                                       "package com.intellij.samples;\n" +
                                                       "\n" +
                                                       "import com.intellij.idea.Main;\n" +
                                                       "\n" +
                                                       "import javax.swing.*;\n" +
                                                       "import java.util.Vector;\n" +
                                                       "\n" +
                                                       "public class Foo {\n" +
                                                       "  private int field1;\n" +
                                                       "  private int field2;\n" +
                                                       "\n" +
                                                       "  public void foo1() {\n" +
                                                       "\n" +
                                                       "  }\n" +
                                                       "\n" +
                                                       "  public void foo2() {\n" +
                                                       "  }\n" +
                                                       "\n" +
                                                       "}";

  private static final String INDENT_AND_BRACES_SAMPLE = "public class Foo {\n" +
                                                         "  public Foo foo;\n" +
                                                         "  public int[] X = new int[]{1, 3, 5 7, 9, 11};\n" +
                                                         "\n" +
                                                         "  public void foo(boolean a, int x, int y, int z) {\n" +
                                                         "    label1:\n" +
                                                         "    do {\n" +
                                                         "      try {\n" +
                                                         "        if (x > 0) {\n" +
                                                         "          int someVariable = a ? x : y;\n" +
                                                         "          int anotherVariable = a ? x : y;\n" +
                                                         "        }\n" +
                                                         "        else if (x < 0) {\n" +
                                                         "          int someVariable = (y + z);\n" +
                                                         "          someVariable = x = x + y;\n" +
                                                         "        }\n" +
                                                         "        else {\n" +
                                                         "          label2:\n" +
                                                         "          for (int i = 0; i < 5; i++) doSomething(i);\n" +
                                                         "        }\n" +
                                                         "        switch (a) {\n" +
                                                         "          case 0:\n" +
                                                         "            doCase0();\n" +
                                                         "            break;\n" +
                                                         "          default:\n" +
                                                         "            doDefault();\n" +
                                                         "        }\n" +
                                                         "      }\n" +
                                                         "      catch (Exception e) {\n" +
                                                         "        processException(e.getMessage(), x + y, z, a);\n" +
                                                         "      }\n" +
                                                         "      finally {\n" +
                                                         "        processFinally();\n" +
                                                         "      }\n" +
                                                         "    }\n" +
                                                         "    while (true);\n" +
                                                         "\n" +
                                                         "    if (2 < 3) return;\n" +
                                                         "    if (3 < 4) return;\n" +
                                                         "    do {\n" +
                                                         "      x++\n" +
                                                         "    }\n" +
                                                         "    while (x < 10000);\n" +
                                                         "    while (x < 50000) x++;\n" +
                                                         "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
                                                         "    foo.fooBar().fooBar()\n" +
                                                         "       .fooBar().foo.fooBar().fooBar()\n" +
                                                         "                    .fooBar();\n" +
                                                         "  }\n" +
                                                         "\n" +
                                                         "  public Foo fooBar() {\n" +
                                                         "    return this;\n" +
                                                         "  }\n" +
                                                         "\n" +
                                                         "  private class InnerClass implements I1, I2 {\n" +
                                                         "    public void bar() throws E1, E2 {\n" +
                                                         "    }\n" +
                                                         "  }\n" +
                                                         "}";

  private static final String SPACING_SAMPLE = "@Annotation(param1 = \"value1\", param2 = \"value2\")\n" +
                                               "public class Foo<T, U> {\n" +
                                               "  int[] X = new int[]{1, 3, 5, 6, 7, 87, 1213, 2};\n" +
                                               "\n" +
                                               "  public void foo(int x, int y) {\n" +
                                               "    for (int i = 0; i < x; i++) {\n" +
                                               "      y += (y ^ 0x123) << 2;\n" +
                                               "    }\n" +
                                               "    do {\n" +
                                               "      try {\n" +
                                               "        if (0 < x && x < 10) {\n" +
                                               "          while (x != y) {\n" +
                                               "            x = f(x * 3 + 5);\n" +
                                               "          }\n" +
                                               "        }\n" +
                                               "        else {\n" +
                                               "          synchronized (this) {\n" +
                                               "            switch (e.getCode()) {\n" +
                                               "              //...\n" +
                                               "            }\n" +
                                               "          }\n" +
                                               "        }\n" +
                                               "      }\n" +
                                               "      catch (MyException e) {\n" +
                                               "      }\n" +
                                               "      finally {\n" +
                                               "        int[] arr = (int[])g(y);\n" +
                                               "        x = y >= 0 ? arr[y] : -1;\n" +
                                               "      }\n" +
                                               "    }\n" +
                                               "    while (true);\n" +
                                               "  }\n" +
                                               "}";

  private static final String WRAPPING_CODE_SAMPLE = "/*\n" +
                                                     " * This is a sample file.\n" +
                                                     " */\n" +
                                                     "\n" +
                                                     "public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
                                                     "  private int f1;\n" +
                                                     "  private int f2;\n" +
                                                     "  public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}\n" +
                                                     "  public static void longerMethod() throws Exception1, Exception2, Exception3 {\n" +
                                                     "    int[] a = new int[] {1, 2, 0x0052, 0x0053, 0x0054};\n" +
                                                     "    foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057);\n" +
                                                     "    int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF);\n" +
                                                     "    String s1, s2, s3;\n" +
                                                     "    s1 = s2 = s3 = \"012345678901456\";\n" +
                                                     "    assert i + j + k + l + n+ m <= 2 : \"assert description\";" +
                                                     "    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13;\n" +
                                                     "    label: " +
                                                     "    for (int i = 0; i < 0xFFFFFF; i += 2) {\n" +
                                                     "       super.getFoo().foo().getBar().bar();\n" +
                                                     "    }\n" +
                                                     "  }\n" +
                                                     "}\n" +
                                                     "\n" +
                                                     "enum Breed {\n" +
                                                     "    Dalmatian(), Labrador(), Dachshund()\n" +
                                                     "}\n" +
                                                     "\n" +
                                                     "@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {\n" +
                                                     "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static void foo(){\n" +
                                                     "    }\n" +
                                                     "    @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") public static int myFoo;\n" +
                                                     "    public void method(@Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int param){\n" +
                                                     "        @Annotation1 @Annotation3(param1=\"value1\", param2=\"value2\") final int localVariable;" +
                                                     "    }\n" +
                                                     "}";
}
