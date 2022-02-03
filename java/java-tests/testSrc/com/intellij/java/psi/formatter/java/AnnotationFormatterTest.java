// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class AnnotationFormatterTest extends JavaFormatterTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final CodeStyleSettingsManager codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(getProject());
    codeStyleSettingsManager.setTemporarySettings(CodeStyle.createTestSettings());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getBasePath() {
    return null;
  }

  public void testAnnotationWrapping() {

    getSettings(JavaLanguage.INSTANCE).PARAMETER_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings(JavaLanguage.INSTANCE).VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest("class Foo {\n" +
               "    public void foo(@Ann1 @Ann2 int p1, @Ann3 boolean p1){}\n" +
               "}",
               "class Foo {\n" +
               "    public void foo(\n" +
               "            @Ann1\n" +
               "            @Ann2\n" +
               "            int p1,\n" +
               "            @Ann3\n" +
               "            boolean p1) {\n" +
               "    }\n" +
               "}");

    doTextTest("public interface PsiClass{\n" +
               "  @Nullable(documentation = \"return null for anonymous and local classes, and for type parameters\", doc2=\"\")\n" +
               "  String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"return null for anonymous and local classes, and for type parameters\", doc2 = \"\")\n" +
               "    String getQualifiedName();\n" +
               "}");

    getSettings(JavaLanguage.INSTANCE).CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest("@Ann1 @Ann2 class Foo {}",
               "@Ann1\n" +
               "@Ann2\n" +
               "class Foo {\n" +
               "}");

    doTextTest("@Ann1 @Ann2 interface Foo {}",
               "@Ann1\n" +
               "@Ann2\n" +
               "interface Foo {\n" +
               "}");

    doTextTest("class Foo { @Ann1 @Ann2 public static int myField;}",
               "class Foo {\n" +
               "    @Ann1\n" +
               "    @Ann2\n" +
               "    public static int myField;\n" +
               "}");

    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        @Ann1 @Ann2 final int i = 0;" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        @Ann1\n" +
               "        @Ann2\n" +
               "        final int i = 0;\n" +
               "    }\n" +
               "}");

    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 40;

    doTextTest("@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {}",
               "@Annotation1 @Annotation2\n" +
               "@Annotation3(param1 = \"value1\",\n" +
               "        param2 = \"value2\") @Annotation4\n" +
               "class Foo {\n" +
               "}");

  }

  public void testAnnotationParametersWrapping() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = false;
    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"parameter1 value\",\n" +
               "            doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"parameter1 value\",\n" +
               "              doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");

  }

  public void testAnnotationParameters_DoNotWrap() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\", second = \"my second line\", third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}",
               "class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\", second = \"my second line\", third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}");
  }

  public void testAnnotationParameters_ChopDownIfLong() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    String initial = "class Test {\n" +
                     "    @SuperAnnotation(first = \"my first line\", second = \"my second line\", third = \"third line\")\n" +
                     "    public void run() {\n" +
                     "    }\n" +
                     "}";

    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest(initial,
               "class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\",\n" +
               "                     second = \"my second line\",\n" +
               "                     third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}");

    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = false;
    doTextTest(initial,
               "class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\",\n" +
               "            second = \"my second line\",\n" +
               "            third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}");
  }

  public void testAnnotationParameters_WrapAlways() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 150;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\", second = \"my second line\", third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}",
               "class Test {\n" +
               "    @SuperAnnotation(first = \"my first line\",\n" +
               "                     second = \"my second line\",\n" +
               "                     third = \"third line\")\n" +
               "    public void run() {\n" +
               "    }\n" +
               "}");
  }

  public void testSpaces1() {
    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testSpaces2() {
    getSettings(JavaLanguage.INSTANCE).SPACE_BEFORE_ANOTATION_PARAMETER_LIST = true;

    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable (documentation = \"parameter1 value\", doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testSpaces3() {
    getCustomJavaSettings().SPACE_AROUND_ANNOTATION_EQ = false;
    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation=\"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testSpaces4() {
    getCustomJavaSettings().SPACE_AROUND_ANNOTATION_EQ = true;

    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testSpaces6() {
    getSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_ANNOTATION_PARENTHESES = true;

    doTextTest("public interface PsiClass{\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2=\"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable( documentation = \"parameter1 value\", doc2 = \"parameter2 value\" )\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testSpaces7() {
    doTextTest("public interface PsiClass{\n" +
               "    @ Nullable  ( documentation    =  \"parameter1 value\"   ,doc2=\"parameter2 value\"   )  \n" +
               "    String getQualifiedName();\n" +
               "}",
               "public interface PsiClass {\n" +
               "    @Nullable(documentation = \"parameter1 value\", doc2 = \"parameter2 value\")\n" +
               "    String getQualifiedName();\n" +
               "}");
  }

  public void testAnnotationInterface() {
    doTextTest(" @ Documented\n" +
               " @ Retention(RetentionPolicy.CLASS)\n" +
               " @  Target  (   { ElementType.METHOD  ,ElementType.FIELD ,   ElementType.PARAMETER,ElementType.LOCAL_VARIABLE})\n" +
               "public@interface NotNull{\n" +
               "String     documentation (   )   default    \"\"   ;\n" +
               "String     myField   =    \"\"   ;\n" +
               "}",
               "@Documented\n" +
               "@Retention(RetentionPolicy.CLASS)\n" +
               "@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})\n" +
               "public @interface NotNull {\n" +
               "    String documentation() default \"\";\n" +
               "\n" +
               "    String myField = \"\";\n" +
               "}");
  }

  public void testEnumFormatting() {
    getSettings(JavaLanguage.INSTANCE).ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
      doTextTest("  enum Breed {\n" +
                 "     Dalmatian (  \"spotted\" ),Labrador ( \"black\" ),Dachshund( \"brown\" );\n" +
                 "\n" +
                 "     // constructor\n" +
                 "     Breed ( String colour )\n" +
                 "        {\n" +
                 "        this.colour = colour;\n" +
                 "        }\n" +
                 "\n" +
                 "     private String colour;\n" +
                 "\n" +
                 "     // additional method of every Breed object\n" +
                 "     public String getColour()\n" +
                 "        {\n" +
                 "        return colour;\n" +
                 "        }\n" +
                 "  }", "enum Breed {\n" + "    Dalmatian(\"spotted\"),\n" + "    Labrador(\"black\"),\n" + "    Dachshund(\"brown\");\n" +
                        "\n" + "    // constructor\n" + "    Breed(String colour) {\n" + "        this.colour = colour;\n" + "    }\n" +
                                                                                                                                       "\n" +
                                                                                                                                            "    private String colour;\n" +
                                                                                                                                                                           "\n" +
                                                                                                                                                                                "    // additional method of every Breed object\n" +
                                                                                                                                                                                                                                   "    public String getColour() {\n" +
                                                                                                                                                                                                                                                                       "        return colour;\n" +
                                                                                                                                                                                                                                                                                                  "    }\n" +
                                                                                                                                                                                                                                                                                                            "}");
      doTextTest("  enum Breed {\n" + "     Dalmatian (  \"spotted\" ),Labrador ( \"black\" ),Dachshund( \"brown\" )\n" + "  }",
                 "enum Breed {\n" + "    Dalmatian(\"spotted\"),\n" + "    Labrador(\"black\"),\n" + "    Dachshund(\"brown\")\n" + "}");
      doTextTest("enum Command { \n" + "        USED \n" + "        , \n" + "        UNUSED\n" + "; \n" + " }",
                 "enum Command {\n" + "    USED,\n" + "    UNUSED;\n" + "}");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(effectiveLanguageLevel);
    }
  }


  public void testIdea161875() {
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest(
      "public class Test {\n" +
      "    @SuppressWarnings({\n" +
      "   \"unchecked\",\n" +
      "    \"rawtypes\"\n" +
      "    })\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    @SuppressWarnings({\n" +
      "            \"unchecked\",\n" +
      "            \"rawtypes\"\n" +
      "    })\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}"
    );
  }


  public void testWrapAfterLparInAnnotation() {
    getCustomJavaSettings().NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = true;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().RIGHT_MARGIN = 40;
    doTextTest(
      "@Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf=\"asdasdasdsad\", ashskjdhsajkhdkjasd = \"22222\")\n" +
      "class A {}" +
      "\n",

      "@Foo(\n" +
      "        ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf = \"asdasdasdsad\",\n" +
      "        ashskjdhsajkhdkjasd = \"22222\")\n" +
      "class A {\n" +
      "}\n");
  }

  public void testWrapBeforeRparInAnnotation() {
    getCustomJavaSettings().RPAREN_ON_NEW_LINE_IN_ANNOTATION = true;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      "@Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf=\"asdasdasdsad\", ashskjdhsajkhdkjasd = \"22222\")\n" +
      "class A {}" +
      "\n",

      "@Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf = \"asdasdasdsad\",\n" +
      "        ashskjdhsajkhdkjasd = \"22222\"\n" +
      ")\n" +
      "class A {\n" +
      "}\n");
  }

  public void testTypeAfterAnnotationInParametersNotIndented() {
    doTextTest(
      "class Cls {\n" +
      "    void foo(\n" +
      "            @Bar\n" +
      "            BarObj bar\n" +
      "    ) {}\n" +
      "}\n",

      "class Cls {\n" +
      "    void foo(\n" +
      "            @Bar\n" +
      "            BarObj bar\n" +
      "    ) {\n" +
      "    }\n" +
      "}\n");
  }

  public void testAnnotationShouldNotBreakAfterKeyword() {
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest(
      "class A {\n"+
      "  @AnnBefore private @AnnAfter void foo() {\n" +
      "  }\n" +
      "}",

      "class A {\n" +
      "    @AnnBefore\n" +
      "    private @AnnAfter void foo() {\n" +
      "    }\n" +
      "}"
    );
  }
}
