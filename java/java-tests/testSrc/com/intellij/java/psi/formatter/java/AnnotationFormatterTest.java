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
    doTextTest("""
                 class Foo {
                     public void foo(@Ann1 @Ann2 int p1, @Ann3 boolean p1){}
                 }""",
               """
                 class Foo {
                     public void foo(
                             @Ann1
                             @Ann2
                             int p1,
                             @Ann3
                             boolean p1) {
                     }
                 }""");

    doTextTest("""
                 public interface PsiClass{
                   @Nullable(documentation = "return null for anonymous and local classes, and for type parameters", doc2="")
                   String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "return null for anonymous and local classes, and for type parameters", doc2 = "")
                     String getQualifiedName();
                 }""");

    getSettings(JavaLanguage.INSTANCE).CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest("@Ann1 @Ann2 class Foo {}",
               """
                 @Ann1
                 @Ann2
                 class Foo {
                 }""");

    doTextTest("@Ann1 @Ann2 interface Foo {}",
               """
                 @Ann1
                 @Ann2
                 interface Foo {
                 }""");

    doTextTest("class Foo { @Ann1 @Ann2 public static int myField;}",
               """
                 class Foo {
                     @Ann1
                     @Ann2
                     public static int myField;
                 }""");

    doTextTest("""
                 class Foo {
                     void foo() {
                         @Ann1 @Ann2 final int i = 0;    }
                 }""",
               """
                 class Foo {
                     void foo() {
                         @Ann1
                         @Ann2
                         final int i = 0;
                     }
                 }""");

    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).CLASS_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 40;

    doTextTest("@Annotation1 @Annotation2 @Annotation3(param1=\"value1\", param2=\"value2\") @Annotation4 class Foo {}",
               """
                 @Annotation1 @Annotation2
                 @Annotation3(param1 = "value1",
                         param2 = "value2") @Annotation4
                 class Foo {
                 }""");

  }

  public void testAnnotationParametersWrapping() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = false;
    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "parameter1 value",
                             doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "parameter1 value",
                               doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");

  }

  public void testAnnotationParameters_DoNotWrap() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("""
                 class Test {
                     @SuperAnnotation(first = "my first line", second = "my second line", third = "third line")
                     public void run() {
                     }
                 }""",
               """
                 class Test {
                     @SuperAnnotation(first = "my first line", second = "my second line", third = "third line")
                     public void run() {
                     }
                 }""");
  }

  public void testAnnotationParameters_ChopDownIfLong() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 50;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    String initial = """
      class Test {
          @SuperAnnotation(first = "my first line", second = "my second line", third = "third line")
          public void run() {
          }
      }""";

    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest(initial,
               """
                 class Test {
                     @SuperAnnotation(first = "my first line",
                                      second = "my second line",
                                      third = "third line")
                     public void run() {
                     }
                 }""");

    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = false;
    doTextTest(initial,
               """
                 class Test {
                     @SuperAnnotation(first = "my first line",
                             second = "my second line",
                             third = "third line")
                     public void run() {
                     }
                 }""");
  }

  public void testAnnotationParameters_WrapAlways() {
    getSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN = 150;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("""
                 class Test {
                     @SuperAnnotation(first = "my first line", second = "my second line", third = "third line")
                     public void run() {
                     }
                 }""",
               """
                 class Test {
                     @SuperAnnotation(first = "my first line",
                                      second = "my second line",
                                      third = "third line")
                     public void run() {
                     }
                 }""");
  }

  public void testSpaces1() {
    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "parameter1 value", doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");
  }

  public void testSpaces2() {
    getSettings(JavaLanguage.INSTANCE).SPACE_BEFORE_ANOTATION_PARAMETER_LIST = true;

    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable (documentation = "parameter1 value", doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");
  }

  public void testSpaces3() {
    getCustomJavaSettings().SPACE_AROUND_ANNOTATION_EQ = false;
    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation="parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""");
  }

  public void testSpaces4() {
    getCustomJavaSettings().SPACE_AROUND_ANNOTATION_EQ = true;

    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "parameter1 value", doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");
  }

  public void testSpaces6() {
    getSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_ANNOTATION_PARENTHESES = true;

    doTextTest("""
                 public interface PsiClass{
                     @Nullable(documentation = "parameter1 value", doc2="parameter2 value")
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable( documentation = "parameter1 value", doc2 = "parameter2 value" )
                     String getQualifiedName();
                 }""");
  }

  public void testSpaces7() {
    doTextTest("""
                 public interface PsiClass{
                     @ Nullable  ( documentation    =  "parameter1 value"   ,doc2="parameter2 value"   ) \s
                     String getQualifiedName();
                 }""",
               """
                 public interface PsiClass {
                     @Nullable(documentation = "parameter1 value", doc2 = "parameter2 value")
                     String getQualifiedName();
                 }""");
  }

  public void testAnnotationInterface() {
    doTextTest("""
                  @ Documented
                  @ Retention(RetentionPolicy.CLASS)
                  @  Target  (   { ElementType.METHOD  ,ElementType.FIELD ,   ElementType.PARAMETER,ElementType.LOCAL_VARIABLE})
                 public@interface NotNull{
                 String     documentation (   )   default    ""   ;
                 String     myField   =    ""   ;
                 }""",
               """
                 @Documented
                 @Retention(RetentionPolicy.CLASS)
                 @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
                 public @interface NotNull {
                     String documentation() default "";

                     String myField = "";
                 }""");
  }

  public void testEnumFormatting() {
    getSettings(JavaLanguage.INSTANCE).ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
      doTextTest("""
                     enum Breed {
                        Dalmatian (  "spotted" ),Labrador ( "black" ),Dachshund( "brown" );

                        // constructor
                        Breed ( String colour )
                           {
                           this.colour = colour;
                           }

                        private String colour;

                        // additional method of every Breed object
                        public String getColour()
                           {
                           return colour;
                           }
                     }\
                   """, """
                   enum Breed {
                       Dalmatian("spotted"),
                       Labrador("black"),
                       Dachshund("brown");

                       // constructor
                       Breed(String colour) {
                           this.colour = colour;
                       }

                       private String colour;

                       // additional method of every Breed object
                       public String getColour() {
                           return colour;
                       }
                   }""");
      doTextTest("""
                     enum Breed {
                        Dalmatian (  "spotted" ),Labrador ( "black" ),Dachshund( "brown" )
                     }\
                   """,
                 """
                   enum Breed {
                       Dalmatian("spotted"),
                       Labrador("black"),
                       Dachshund("brown")
                   }""");
      doTextTest("""
                   enum Command {\s
                           USED\s
                           ,\s
                           UNUSED
                   ;\s
                    }""",
                 """
                   enum Command {
                       USED,
                       UNUSED;
                   }""");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(effectiveLanguageLevel);
    }
  }


  public void testIdea161875() {
    getCustomJavaSettings().ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    doTextTest(
      """
        public class Test {
            @SuppressWarnings({
           "unchecked",
            "rawtypes"
            })
            void foo() {
            }
        }""",

      """
        public class Test {
            @SuppressWarnings({
                    "unchecked",
                    "rawtypes"
            })
            void foo() {
            }
        }"""
    );
  }


  public void testWrapAfterLparInAnnotation() {
    getCustomJavaSettings().NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = true;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().RIGHT_MARGIN = 40;
    doTextTest(
      """
        @Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf="asdasdasdsad", ashskjdhsajkhdkjasd = "22222")
        class A {}
        """,

      """
        @Foo(
                ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf = "asdasdasdsad",
                ashskjdhsajkhdkjasd = "22222")
        class A {
        }
        """);
  }

  public void testWrapBeforeRparInAnnotation() {
    getCustomJavaSettings().RPAREN_ON_NEW_LINE_IN_ANNOTATION = true;
    getCustomJavaSettings().ANNOTATION_PARAMETER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().RIGHT_MARGIN = 40;

    doTextTest(
      """
        @Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf="asdasdasdsad", ashskjdhsajkhdkjasd = "22222")
        class A {}
        """,

      """
        @Foo(ghfhfjfghfhgfhjgfgjhfghfhfjfghfhgfhjgfgjhf = "asdasdasdsad",
                ashskjdhsajkhdkjasd = "22222"
        )
        class A {
        }
        """);
  }

  public void testTypeAfterAnnotationInParametersNotIndented() {
    doTextTest(
      """
        class Cls {
            void foo(
                    @Bar
                    BarObj bar
            ) {}
        }
        """,

      """
        class Cls {
            void foo(
                    @Bar
                    BarObj bar
            ) {
            }
        }
        """);
  }

  public void testAnnotationShouldNotBreakAfterKeyword() {
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest(
      """
        class A {
          @AnnBefore private @AnnAfter void foo() {
          }
        }""",

      """
        class A {
            @AnnBefore
            private @AnnAfter void foo() {
            }
        }"""
    );
  }

  public void testAnnotationEnumFieldsDoNotWrap() {
    getCustomJavaSettings().ENUM_FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("""
                 public enum MyEnum {
                     @Anno1 FIRST
                 }
                 """,
               """
                 public enum MyEnum {
                     @Anno1 FIRST
                 }
                 """);
  }
  public void testAnnotationEnumFieldsWrapAlways() {
    getCustomJavaSettings().ENUM_FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTextTest("""
                 public enum MyEnum {
                     @Anno1 FIRST
                 }
                 """,
               """
                 public enum MyEnum {
                     @Anno1
                     FIRST
                 }
                 """);
  }

  public void testAnnotationsEnumFieldsWrapAsNeeded() {
    getCustomJavaSettings().ENUM_FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 20;
    doTextTest("""
                 public enum MyEnum {
                     @Anno1 @Anno2 @Anno3 @Anno4 FIRST
                 }
                 """,
               """
                 public enum MyEnum {
                     @Anno1 @Anno2
                     @Anno3 @Anno4
                     FIRST
                 }
                 """);
  }

  public void testAnnotationsEnumFieldsWrapAlwaysWithDoNotWrapOnFields() {
    getCustomJavaSettings().ENUM_FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest("""
                 public enum MyEnum {
                     @Anno1 @Anno2
                     @Anno3 @Anno4
                     FIRST, SECOND, @Anno1 @Anno2 THIRD
                 }
                 """,
               """
                 public enum MyEnum {
                     @Anno1
                     @Anno2
                     @Anno3
                     @Anno4
                     FIRST, SECOND,
                     @Anno1
                     @Anno2
                     THIRD
                 }
                 """);
  }

  public void testTypeAnnotationsForMethodReturnTypeAreOnTheSameLineWhenAnyModifierIsPresent() {
    doTextTest("""
                 public class Foo {
                 public static synchronized @Nullable @Nls String bar() {
                     return "";
                 }
                 }
                 """,
               """
                 public class Foo {
                     public static synchronized @Nullable @Nls String bar() {
                         return "";
                     }
                 }
                 """);
  }

  public void testTypeAnnotationsForMethodReturnTypeAreOnTheDifferentLinesWhenNoModifiersArePresent() {
    doTextTest("""
                 public class Foo {
                 @Nullable @Nls String bar() {
                     return "";
                 }
                 }
                 """,
               """
                 public class Foo {
                     @Nullable
                     @Nls
                     String bar() {
                         return "";
                     }
                 }
                 """);
  }
}
