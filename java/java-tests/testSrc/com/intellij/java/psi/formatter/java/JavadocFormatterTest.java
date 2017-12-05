// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * @author Denis Zhdanov
 */
@SuppressWarnings("SpellCheckingInspection")
public class JavadocFormatterTest extends AbstractJavaFormatterTest {
  public void testRightMargin() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      "/** Here is one-line java-doc comment */" +
      "class Foo {\n" +
      "}",

      "/**\n" +
      " * Here is one-line java-doc \n" +
      " * comment\n" +
      " */\n" +
      "class Foo {\n" +
      "}");
  }

  public void testNoFormattingIfStartsWithLotsOfAsterisks() {
    doTextTest(
      "/****\n" +
      " * description\n" +
      " *\n" +
      " *\n" +
      " * xxxx\n" +
      " */\n" +
      "      class X {}",

      "/****\n" +
      " * description\n" +
      " *\n" +
      " *\n" +
      " * xxxx\n" +
      " */\n" +
      "class X {\n" +
      "}");
  }

  public void testDoNotWrapLink() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 70;

    doTextTest(
      "/**\n" +
      " * Some of the usl contained {@link sdfsdf.test.ttttttt.ssss.stttt.tttttttcom}\n" +
      " */\n" +
      "            public class X {\n" +
      "}",

      "/**\n" +
      " * Some of the usl contained \n" +
      " * {@link sdfsdf.test.ttttttt.ssss.stttt.tttttttcom}\n" +
      " */\n" +
      "public class X {\n" +
      "}");
  }

  public void testDoNot() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 70;

    doTextTest(
      "/**\n" +
      " * Some of the usl contained <a href=\"http://martinfowler.com/articles/replaceThrowWithNotification.html\">\n" +
      " */\n" +
      "            public class X {\n" +
      "}",

      "/**\n" +
      " * Some of the usl contained \n" +
      " * <a href=\"http://martinfowler.com/articles/replaceThrowWithNotification.html\">\n" +
      " */\n" +
      "public class X {\n" +
      "}");
  }

  public void testPackageJavadoc() {
    doTextTest(
      "/**\n" +
      " *              super auper\n" +
      " */\n" +
      " package com;\n",

      "/**\n" +
      " * super auper\n" +
      " */\n" +
      "package com;\n");
  }

  public void testDoWrapOnAsterisks() {
    doTextTest(
      "/***********\n" +
      " *\n" +
      " *********************/\n" +
      "\n" +
      "\n" +
      "   public class Test {\n" +

      "}\n",
      "/***********\n" +
      " *\n" +
      " *********************/\n" +
      "\n" +
      "\n" +
      "public class Test {\n" +
      "}\n");
  }

  public void testWrapAfterAsterisks() {
    doTextTest(
      "/** hollla la\n" +
      " * I am javadoc comment\n" +
      " * heey ***********/\n" +
      "   class T {   }\n",

      "/**\n" +
      " * hollla la\n" +
      " * I am javadoc comment\n" +
      " * heey\n" +
      " ***********/\n" +
      "class T {\n" +
      "}\n");
  }

  public void testStrangeComment() {
    doTextTest(
      "/**F*****/\n" +
      "public class T {\n" +
      "}",

      "/**\n" +
      " * F\n" +
      " *****/\n" +
      "public class T {\n" +
      "}");
  }

  public void testIncompleteJavadoc() {
    doTextTest("/**\n", "/**\n");
  }

  public void testEA49739() {
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 35;
    getSettings().WRAP_COMMENTS = true;

    doTextTest(
      "class A {\n" +
      "    /**\n" +
      "     * @return a is one line javadoc\n" +
      "     */\n" +
      "    public int get(int a) {\n" +
      "        return 1;\n" +
      "    }\n" +
      "  }",

      "class A {\n" +
      "    /**\n" +
      "     * @return a is one line \n" +
      "     * javadoc\n" +
      "     */\n" +
      "    public int get(int a) {\n" +
      "        return 1;\n" +
      "    }\n" +
      "}");
  }

  public void testOneLineCommentWrappedByRightMarginIntoMultiLine() {
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 35;

    doTextTest(
      "/** Here is one-line java-doc comment */" +
      "class Foo {\n" +
      "}",

      "/**\n" +
      " * Here is one-line java-doc\n" +
      " * comment\n" +
      " */\n" +
      "class Foo {\n" +
      "}");
  }

  public void testLineFeedsArePreservedDuringWrap() {
    // Inspired by IDEA-61895
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().JD_PRESERVE_LINE_FEEDS = true;
    getSettings().RIGHT_MARGIN = 48;

    doTextTest(
      "/**\n" +
      " * This is a long comment that spans more than one\n" +
      " * line\n" +
      " */\n" +
      "class Test {\n" +
      "}",

      "/**\n" +
      " * This is a long comment that spans more than\n" +
      " * one\n" +
      " * line\n" +
      " */\n" +
      "class Test {\n" +
      "}");
  }

  public void testSCR11296() {
    CommonCodeStyleSettings settings = getSettings();
    settings.RIGHT_MARGIN = 50;
    settings.WRAP_COMMENTS = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_P_AT_EMPTY_LINES = false;
    getJavaSettings().JD_KEEP_EMPTY_LINES = false;
    doTest();
  }

  public void testSCR2632() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().RIGHT_MARGIN = 20;

    doTextTest(
      "/**\n" +
      " * <p />\n" +
      " * Another paragraph of the description placed after blank line.\n" +
      " */\n" +
      "class A{}",

      "/**\n" +
      " * <p/>\n" +
      " * Another paragraph\n" +
      " * of the description\n" +
      " * placed after\n" +
      " * blank line.\n" +
      " */\n" +
      "class A {\n" +
      "}");
  }

  public void testPreserveExistingSelfClosingTagsAndGenerateOnlyPTag() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);

    doTextTest(
      "/**\n" +
      " * My test comment\n" +
      " * <p/>\n" +
      " * \n" +
      " * With empty line\n" +
      " */\n" +
      "class T {\n" +
      "}",

      "/**\n" +
      " * My test comment\n" +
      " * <p/>\n" +
      " * <p>\n" +
      " * With empty line\n" +
      " */\n" +
      "class T {\n" +
      "}");
  }

  public void testParagraphTagGeneration() {
    // Inspired by IDEA-61811
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_P_AT_EMPTY_LINES = true;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);

    doTextTest(
      "/**\n" +
      " * line 1\n" +
      " *\n" +
      " * line 2\n" +
      " * <pre>\n" +
      " *   line 3\n" +
      " *\n" +
      " *   line 4\n" +
      " * </pre>\n" +
      " */\n" +
      "class Test {\n" +
      "}",

      "/**\n" +
      " * line 1\n" +
      " * <p>\n" +
      " * line 2\n" +
      " * <pre>\n" +
      " *   line 3\n" +
      " *\n" +
      " *   line 4\n" +
      " * </pre>\n" +
      " */\n" +
      "class Test {\n" +
      "}");
  }

  public void testParameterDescriptionNotOnNewLine() {
    // IDEA-107383
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_ALIGN_PARAM_COMMENTS = true;

    doClassTest(
      "/**\n" +
      " @param protocolId protocol id\n" +
      " @param connectedUserIdHandlerFromServer user id\n" +
      " @return\n" +

      " */\n" +
      "public void register(int protocolId, int connectedUserIdHandlerFromServer) {\n" +
      "}",

      "/**\n" +
      " * @param protocolId                       protocol id\n" +
      " * @param connectedUserIdHandlerFromServer user id\n" +
      " * @return\n" +
      " */\n" +
      "public void register(int protocolId, int connectedUserIdHandlerFromServer) {\n" +
      "}");
  }

  public void testWrappedParameterDescription() {
    // Inspired by IDEA-13072
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().JD_PARAM_DESCRIPTION_ON_NEW_LINE = true;

    doClassTest(
      "/**\n" +
      " * test description\n" +
      " * @param first first description\n" +
      " * @param second\n" +
      " * @param third third\n" +
      " *              description\n" +
      " * @param forth\n" +
      " *          forth description\n" +
      " */\n" +
      "void test(int first, int second, int third, int forth) {\n" +
      "}",

      "/**\n" +
      " * test description\n" +
      " *\n" +
      " * @param first\n" +
      " *         first description\n" +
      " * @param second\n" +
      " * @param third\n" +
      " *         third description\n" +
      " * @param forth\n" +
      " *         forth description\n" +
      " */\n" +
      "void test(int first, int second, int third, int forth) {\n" +
      "}");
  }

  public void testExceptionAlignmentCorrect() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_ALIGN_EXCEPTION_COMMENTS = true;

    doTextTest(
      "public class Controller {\n" +
      "\n" +
      "    /**\n" +
      "     * @throws NoSearchServersConfiguredException If no search engine servers are configured.\n" +
      "     * @throws SearchServerUnavailableException If the search engine server is not accessible.\n" +
      "     * @throws InvalidSearchServerResponseException If the search engine server response was invalid.\n" +
      "     * @throws NotificationEncodingException If the request could not be encoded to UTF-8.\n" +
      "     * @throws NotificationUnavailableException If the notification server is not available or sent back an invalid response code.\n" +
      "     */\n" +
      "    public int superDangerousMethod() {\n" +
      "        return 68;\n" +
      "    }\n" +
      "}",

      "public class Controller {\n" +
      "\n" +
      "    /**\n" +
      "     * @throws NoSearchServersConfiguredException   If no search engine servers are configured.\n" +
      "     * @throws SearchServerUnavailableException     If the search engine server is not accessible.\n" +
      "     * @throws InvalidSearchServerResponseException If the search engine server response was invalid.\n" +
      "     * @throws NotificationEncodingException        If the request could not be encoded to UTF-8.\n" +
      "     * @throws NotificationUnavailableException     If the notification server is not available or sent back an invalid response code.\n" +
      "     */\n" +
      "    public int superDangerousMethod() {\n" +
      "        return 68;\n" +
      "    }\n" +
      "}");
  }

  public void testDoNotWrapMultiLineCommentIntoOneLine() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true;

    String test =
      "/**\n" +
      " * foo\n" +
      " */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}";
    doClassTest(test, test);
  }

  public void testLeaveOneLineComment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true;

    String test =
      "/** foo */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}";
    doClassTest(test, test);
  }

  public void testWrapOneLineComment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false;

    doClassTest(
      "/** foo */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}",

      "/**\n" +
      " * foo\n" +
      " */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}");
  }

  public void testWrapStrangeComment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false;

    doClassTest(
      "/** foo" +
      " */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}",

      "/**\n" +
      " * foo\n" +
      " */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}");
  }

  public void testWrapStrangeCommentIfNotWrapOneLines() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true;
    doClassTest(
      "/** foo\n" +
      " */" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}",

      "/**\n" +
      " * foo\n" +
      " */\n" +
      "public Object next() {\n" +
      "    return new Object();\n" +
      "}");
  }

  public void testReturnTagAlignment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().RIGHT_MARGIN = 80;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().WRAP_LONG_LINES = true;

    doClassTest(
      "    /**\n" +
      "     * @return this is a return value documentation with a very long description that is longer than the right margin." +
      " It is more than 200 characters long, not including the comment indent and the asterisk characters," +
      " which should be greater than any sane right margin.\n" +
      "     */\n" +
      "    public int method(int parameter) {\n" +
      "        return 0;\n" +
      "    }\n",

      "/**\n" +
      " * @return this is a return value documentation with a very long description\n" +
      " * that is longer than the right margin. It is more than 200 characters\n" +
      " * long, not including the comment indent and the asterisk characters, which\n" +
      " * should be greater than any sane right margin.\n" +
      " */\n" +
      "public int method(int parameter) {\n" +
      "    return 0;\n" +
      "}\n");
  }

  public void testReturnTagAlignmentWithPreTagOnFirstLine() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().RIGHT_MARGIN = 80;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().WRAP_LONG_LINES = true;

    doClassTest(
      "    /**\n" +
      "     * @return <pre>this is a return value documentation with a very long description\n" +
      "     * that is longer than the right margin.</pre>\n" +
      "     */\n" +
      "    public int method(int parameter) {\n" +
      "        return 0;\n" +
      "    }",

      "/**\n" +
      " * @return <pre>this is a return value documentation with a very long\n" +
      " * description\n" +
      " * that is longer than the right margin.</pre>\n" +
      " */\n" +
      "public int method(int parameter) {\n" +
      "    return 0;\n" +
      "}");
  }

  public void testDoNotMergeCommentLines() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getJavaSettings().JD_PRESERVE_LINE_FEEDS = true;
    getSettings().WRAP_COMMENTS = true;

    doClassTest(
      "/**\n" +
      " * Some comment\n" +
      " * 2016\n" +
      " * Date: Mar 03, 2016\n" +
      " *\n" +
      " */\n" +
      "     public class TestCase {\n" +
      "}",

      "/**\n" +
      " * Some comment\n" +
      " * 2016\n" +
      " * Date: Mar 03, 2016\n" +
      " */\n" +
      "public class TestCase {\n" +
      "}");
  }

  public void testSeeTagAlignment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().RIGHT_MARGIN = 80;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().WRAP_LONG_LINES = true;

    doClassTest(
      "    /**\n" +
      "     * @see this is an additional documentation with a very long description that is longer than the right margin." +
      " It is more than 200 characters long, not including the comment indent and the asterisk characters" +
      " which should be greater than any sane right margin\n" +
      "     */\n" +
      "    public int method(int parameter) {\n" +
      "        return 0;\n" +
      "    }",

      "/**\n" +
      " * @see this is an additional documentation with a very long description\n" +
      " * that is longer than the right margin. It is more than 200 characters\n" +
      " * long, not including the comment indent and the asterisk characters which\n" +
      " * should be greater than any sane right margin\n" +
      " */\n" +
      "public int method(int parameter) {\n" +
      "    return 0;\n" +
      "}");
  }

  public void testDummySinceTagAlignment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().RIGHT_MARGIN = 80;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().WRAP_LONG_LINES = true;

    doClassTest(
      "    /**\n" +
      "     * @since this is an additional documentation with a very long description that is longer" +
      " than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters" +
      " which should be greater than any sane right margin\n" +
      "     */\n" +
      "    public int method(int parameter) {\n" +
      "        return 0;\n" +
      "    }",

      "/**\n" +
      " * @since this is an additional documentation with a very long description\n" +
      " * that is longer than the right margin. It is more than 200 characters\n" +
      " * long, not including the comment indent and the asterisk characters which\n" +
      " * should be greater than any sane right margin\n" +
      " */\n" +
      "public int method(int parameter) {\n" +
      "    return 0;\n" +
      "}");
  }

  public void testDummyDeprecatedTagAlignment() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().RIGHT_MARGIN = 80;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().WRAP_COMMENTS = true;
    getSettings().WRAP_LONG_LINES = true;

    doClassTest(
      "    /**\n" +
      "     * @deprecated this is an additional documentation with a very long description that is longer" +
      " than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters" +
      " which should be greater than any sane right margin\n" +
      "     */\n" +
      "    public int method(int parameter) {\n" +
      "        return 0;\n" +
      "    }",

      "/**\n" +
      " * @deprecated this is an additional documentation with a very long\n" +
      " * description that is longer than the right margin. It is more than 200\n" +
      " * characters long, not including the comment indent and the asterisk\n" +
      " * characters which should be greater than any sane right margin\n" +
      " */\n" +
      "public int method(int parameter) {\n" +
      "    return 0;\n" +
      "}");
  }

  public void testJavadocFormattingIndependentOfMethodIndentation() {
    getCurrentCodeStyleSettings().setRightMargin(JavaLanguage.INSTANCE, 50);
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getJavaSettings().JD_P_AT_EMPTY_LINES = false;
    getJavaSettings().JD_KEEP_EMPTY_LINES = false;
    getJavaSettings().JD_ADD_BLANK_AFTER_DESCRIPTION = false;

    formatEveryoneAndCheckIfResultEqual(
      "class A {\n" +
      "    /**\n" +
      "     * Some really great independent test approach purpose live fish\n" +
      "     * banana split string be accurate when writing tests and code\n" +
      "     * read write buffer.\n" +
      "     *\n" +
      "     * Some text after empty line\n" +
      "     *\n" +
      "     */\n" +
      "void foo() {\n" +
      "\n" +
      "}\n" +
      "}",

      "class A {\n" +
      "    /**\n" +
      "     * Some really great independent test approach purpose live fish\n" +
      "     * banana split string be accurate when writing tests and code\n" +
      "     * read write buffer.\n" +
      "     *\n" +
      "     * Some text after empty line\n" +
      "     *\n" +
      "     */\n" +
      "    void foo() {\n" +
      "\n" +
      "    }\n" +
      "}");
  }

  public void testJavadocAlignmentForInnerClasses() {
    getCurrentCodeStyleSettings().setRightMargin(JavaLanguage.INSTANCE, 40);
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;

    doTextTest(
      "public class Outer {\n" +
      "    class Inner {\n" +
      "        /**\n" +
      "         * Password from wild forest big house\n" +
      "         */\n" +
      "        public int getMagic() {\n" +
      "            return 312;\n" +
      "        }\n" +
      "\n" +
      "class InnerInner {\n" +
      "/**\n" +
      " * Special magic needs special rules\n" +
      " */\n" +
      "public int innerMagic() {\n" +
      "    return 1;\n" +
      "}\n" +
      "}\n" +
      "    }\n" +
      "}",

      "public class Outer {\n" +
      "    class Inner {\n" +
      "        /**\n" +
      "         * Password from wild forest big\n" +
      "         * house\n" +
      "         */\n" +
      "        public int getMagic() {\n" +
      "            return 312;\n" +
      "        }\n" +
      "\n" +
      "        class InnerInner {\n" +
      "            /**\n" +
      "             * Special magic needs\n" +
      "             * special rules\n" +
      "             */\n" +
      "            public int innerMagic() {\n" +
      "                return 1;\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testAlignmentWithNoTopClassMembersIndentation() {
    getCurrentCodeStyleSettings().setRightMargin(JavaLanguage.INSTANCE, 40);
    getSettings().WRAP_COMMENTS = true;
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = true;
    getSettings().DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true;

    doTextTest(
      "public class Outer {\n" +
      "class Inner {\n" +
      "/**\n" +
      " * Password from wild forest big\n" +
      " * house\n" +
      " */\n" +
      "public int getMagic() {\n" +
      "    return 312;\n" +
      "}\n" +
      "\n" +
      "class InnerInner {\n" +
      "/**\n" +
      " * Special magic needs special rules\n" +
      " */\n" +
      "public int innerMagic() {\n" +
      "    return 1;\n" +
      "}\n" +
      "\n" +
      "class InnerInnerInner {\n" +
      "int iii;\n" +
      "class TripleInner {\n" +
      "int ti;\n" +
      "}\n" +
      "}\n" +
      "}\n" +
      "}\n" +
      "    public static void main(String[] args) {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "}",

      "public class Outer {\n" +
      "class Inner {\n" +
      "    /**\n" +
      "     * Password from wild forest big\n" +
      "     * house\n" +
      "     */\n" +
      "    public int getMagic() {\n" +
      "        return 312;\n" +
      "    }\n" +
      "\n" +
      "    class InnerInner {\n" +
      "        /**\n" +
      "         * Special magic needs special\n" +
      "         * rules\n" +
      "         */\n" +
      "        public int innerMagic() {\n" +
      "            return 1;\n" +
      "        }\n" +
      "\n" +
      "        class InnerInnerInner {\n" +
      "            int iii;\n" +
      "\n" +
      "            class TripleInner {\n" +
      "                int ti;\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}\n" +
      "\n" +
      "public static void main(String[] args) {\n" +
      "    System.out.println(\"AAA!\");\n" +
      "}\n" +
      "}");
  }

  public void testDoNotWrapLongLineCommentWithSpaceInStart() {
    getSettings().KEEP_FIRST_COLUMN_COMMENT = true;
    getSettings().WRAP_LONG_LINES = true;
    getSettings().RIGHT_MARGIN = 200;

    String test =
      "public class JiraIssue {\n" +
      "\n" +
      "    public static void main(String[] args) {\n" +
      "// AAAMIIGgIBADANBgkqhkiG9w0BAQEFAASCBugwgsdfssdflkldkflskdfsdkfjskdlfjdskjfksdjfksdjfkjsdkfjsdkfjgbkAg" +
      "EAAoIBgQCZfKds4XjFWIU8D4OqCYJ0TkAkKPVV96v2l6PuMBNbON3ndHCVvwoJOJnopfbtFro9eCTCUC9MlAUZBAVdCbPVi3ioqaEN\n" +
      "    }\n" +
      "}";
    doTextTest(test, test);
  }

  public void testNotGenerateSelfClosingPTagIfLanguageLevelJava8() {
    getJavaSettings().JD_P_AT_EMPTY_LINES = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;

    doClassTest(
      "/**\n" +
      " * Super method\n" +
      " *\n" +
      " * Super multiple times\n" +
      " */\n" +
      "public void voo() {\n" +
      "}\n",

      "/**\n" +
      " * Super method\n" +
      " * <p>\n" +
      " * Super multiple times\n" +
      " */\n" +
      "public void voo() {\n" +
      "}\n");
  }

  public void testPTagIfLanguageLevelNotJava8() {
    getJavaSettings().JD_P_AT_EMPTY_LINES = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);

    doClassTest(
      "/**\n" +
      " * Super method\n" +
      " *\n" +
      " * Super multiple times\n" +
      " */\n" +
      "public void voo() {\n" +
      "}\n",

      "/**\n" +
      " * Super method\n" +
      " * <p>\n" +
      " * Super multiple times\n" +
      " */\n" +
      "public void voo() {\n" +
      "}\n");
  }

  public void testDoNotTouchSingleLineComments() {
    getJavaSettings().JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;

    doClassTest(
      "/****** AAAAAAA *******/\n" +
      "  \n" +
      "  public void t() {\n" +
      "         }",

      "/****** AAAAAAA *******/\n" +
      "\n" +
      "public void t() {\n" +
      "}");
  }

  public void testKeepPTags() {
    getJavaSettings().JD_P_AT_EMPTY_LINES = true;
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;

    doClassTest(
      "/**\n" +
      " * <pre>new\n" +
      " * code</pre>\n" +
      " * <p>\n" +
      " * Whatever.\n" +
      " * <p>\n" +
      " * Whatever.\n" +
      "    */\n" +
      "public static void main(String[] args) {\n" +
      "     }",

      "/**\n" +
      " * <pre>new\n" +
      " * code</pre>\n" +
      " * <p>\n" +
      " * Whatever.\n" +
      " * <p>\n" +
      " * Whatever.\n" +
      " */\n" +
      "public static void main(String[] args) {\n" +
      "}");
  }

  public void testTouchNothingInsidePreTag() {
    doClassTest(
      "/**\n" +
      " *   Holla\n" +
      " * <pre>\n" +
      " * @Override\n" +
      " *              Test me\n" +
      " * </pre>\n" +
      " */\n" +
      "public void test() {\n" +
      "}",

      "/**\n" +
      " * Holla\n" +
      " * <pre>\n" +
      " * @Override\n" +
      " *              Test me\n" +
      " * </pre>\n" +
      " */\n" +
      "public void test() {\n" +
      "}");
  }

  public void testContinuationDescriptionFormatting() {
    getCurrentCodeStyleSettings().setRightMargin(JavaLanguage.INSTANCE, 40);
    getCurrentCodeStyleSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2;
    getJavaSettings().JD_INDENT_ON_CONTINUATION = true;
    getJavaSettings().JD_ALIGN_PARAM_COMMENTS = false;
    getJavaSettings().JD_ALIGN_EXCEPTION_COMMENTS = false;
    getSettings().WRAP_COMMENTS = true;

    doClassTest(
      "/**\n" +
      " * Just some random text\n" +
      " * @param aParameter randomness in life does not mean it's easy to generate random text\n" +
      " * @param bParameter another random parameter with qualified epoch\n" +
      " * @author rumor oculus rivierra underground sound\n" +
      " * @myrandomtag just write what you want and cranberries with bicycle\n" +
      " * @return super string with everything involved, be aware\n" +
      " */\n" +
      "String test(int aParameter, int bParameter) {\n" +
      "  return \"\";\n" +
      "}  \n",

      "/**\n" +
      " * Just some random text\n" +
      " *\n" +
      " * @param aParameter randomness in\n" +
      " *   life does not mean it's easy to\n" +
      " *   generate random text\n" +
      " * @param bParameter another\n" +
      " *   random parameter with qualified\n" +
      " *   epoch\n" +
      " * @return super string with\n" +
      " *   everything involved, be aware\n" +
      " * @author rumor oculus rivierra\n" +
      " *   underground sound\n" +
      " * @myrandomtag just write what\n" +
      " *   you want and cranberries with\n" +
      " *   bicycle\n" +
      " */\n" +
      "String test(int aParameter, int bParameter) {\n" +
      "    return \"\";\n" +
      "}\n");
  }

  public void testJavadocWithTabs() {
    doClassTest(
      "\t/**\n" +
      "\t \t *\n" +
      "\t \t *\n" +
      "\t \t */\n" +
      "\tvoid check() {\n" +
      "\t}",

      "/**\n" +
      " *\n" +
      " *\n" +
      " */\n" +
      "void check() {\n" +
      "}");
  }

  public void testMultipleSince() {
    doTextTest(
      "/**\n" +
      " * @since 1.7\n" +
      " * @since 2.0\n" +
      " */\n" +
      "public class C {\n" +
      "}",

      "/**\n" +
      " * @since 1.7\n" +
      " * @since 2.0\n" +
      " */\n" +
      "public class C {\n" +
      "}");
  }

  public void testModuleComment() {
    doTextTest(
      "/**\n" +
      " * A module.\n" +
      " * @uses SomeService\n" +
      " */\n" +
      "module M {\n" +
      "}",

      "/**\n" +
      " * A module.\n" +
      " *\n" +
      " * @uses SomeService\n" +
      " */\n" +
      "module M {\n" +
      "}");
  }
}