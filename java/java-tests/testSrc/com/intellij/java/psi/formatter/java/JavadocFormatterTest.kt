// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel

class JavadocFormatterTest : AbstractJavaFormatterTest() {
  fun testRIGHT_MARGIN() {
    settings.apply {
      WRAP_LONG_LINES = true
      RIGHT_MARGIN = 35
    }
    doTextTest(
      "/** Here is one-line java-doc comment */" +
      "class Foo {\n" +
      "}",

      "/**\n" +
      " * Here is one-line java-doc \n" +
      " * comment\n" +
      " */\n" +
      "class Foo {\n" +
      "}")
  }

  fun testNoFormattingIfStartsWithLotsOfAsterisks() {
    doTextTest(
      """/****
 * description
 *
 *
 * xxxx
 */
      class X {}""",

      """/****
 * description
 *
 *
 * xxxx
 */
class X {
}""")
  }

  fun testDoNotWrapLink() {
    settings.apply {
      WRAP_LONG_LINES = true
      RIGHT_MARGIN = 70
    }

    doTextTest(
      "/**\n" +
      " * Some of the usl contained {@link sdfsdf.test.ttttttt.ssss.stttt.tttttttcom}\n" +
      " */\n" +
      "            public class X {\n" +
      "}",

      "/**\n" +
      " * Some of the usl contained\n" +
      " * {@link sdfsdf.test.ttttttt.ssss.stttt.tttttttcom}\n" +
      " */\n" +
      "public class X {\n" +
      "}")
  }

  fun testNoWrapInALink() {
    settings.apply {
      WRAP_LONG_LINES = true
      RIGHT_MARGIN = 70
    }

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
      "}")
  }

  fun testPackageJavadoc() {
    doTextTest(
      """/**
 *              super auper
 */
 package com;
""",

      """/**
 * super auper
 */
package com;
""")
  }

  fun testDoWrapOnAsterisks() {
    doTextTest(
      """/***********
 *
 *********************/


   public class Test {
}
""",
      """/***********
 *
 *********************/


public class Test {
}
""")
  }

  fun testWrapAfterAsterisks() {
    doTextTest(
      """/** hollla la
 * I am javadoc comment
 * heey ***********/
   class T {   }
""",

      """/**
 * hollla la
 * I am javadoc comment
 * heey
 ***********/
class T {
}
""")
  }

  fun testStrangeComment() {
    doTextTest(
      """/**F*****/
public class T {
}""",

      """/**
 * F
 *****/
public class T {
}""")
  }

  fun testIncompleteJavadoc() {
    doTextTest("/**\n", "/**\n")
  }

  fun testEA49739() {
    settings.apply {
      WRAP_LONG_LINES = true
      RIGHT_MARGIN = 35
      WRAP_COMMENTS = true
    }

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
      "     * @return a is one line\n" +
      "     * javadoc\n" +
      "     */\n" +
      "    public int get(int a) {\n" +
      "        return 1;\n" +
      "    }\n" +
      "}")
  }

  fun testOneLineCommentWrappedByRIGHT_MARGINIntoMultiLine() {
    settings.apply {
      WRAP_COMMENTS = true
      RIGHT_MARGIN = 35
    }
    javaSettings.ENABLE_JAVADOC_FORMATTING = true
    javaSettings.JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true

    doTextTest(
      """/** Here is one-line java-doc comment */class Foo {
}""",

      """/**
 * Here is one-line java-doc
 * comment
 */
class Foo {
}""")
  }

  fun testLineFeedsArePreservedDuringWrap() {
    // Inspired by IDEA-61895
    settings.apply {
      WRAP_COMMENTS = true
      RIGHT_MARGIN = 48
    }
    javaSettings.JD_PRESERVE_LINE_FEEDS = true

    doTextTest(
      """/**
 * This is a long comment that spans more than one
 * line
 */
class Test {
}""",

      """/**
 * This is a long comment that spans more than
 * one
 * line
 */
class Test {
}""")
  }

  fun testSCR11296() {
    settings.apply {
      RIGHT_MARGIN = 50
      WRAP_COMMENTS = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_P_AT_EMPTY_LINES = false
      JD_KEEP_EMPTY_LINES = false
    }
    doTest()
  }

  fun testSCR2632() {
    settings.apply {
      WRAP_COMMENTS = true
      RIGHT_MARGIN = 20
    }
    javaSettings.ENABLE_JAVADOC_FORMATTING = true

    doTextTest(
      """/**
 * <p />
 * Another paragraph of the description placed after blank line.
 */
class A{}""",

      """/**
 * <p/>
 * Another paragraph
 * of the
 * description
 * placed after
 * blank line.
 */
class A {
}""")
  }

  fun testPreserveExistingSelfClosingTagsAndGenerateOnlyPTag() {
    javaSettings.ENABLE_JAVADOC_FORMATTING = true
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_7

    doTextTest(
      """/**
 * My test comment
 * <p/>
 *
 * With empty line
 */
class T {
}""",

      """/**
 * My test comment
 * <p/>
 * <p>
 * With empty line
 */
class T {
}""")
  }

  fun testParagraphTagGeneration() {
    // Inspired by IDEA-61811
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_P_AT_EMPTY_LINES = true
    }
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_7

    doTextTest(
      """/**
 * line 1
 *
 * line 2
 * <pre>
 *   line 3
 *
 *   line 4
 * </pre>
 */
class Test {
}""",

      """/**
 * line 1
 * <p>
 * line 2
 * <pre>
 *   line 3
 *
 *   line 4
 * </pre>
 */
class Test {
}""")
  }

  fun testParameterDescriptionNotOnNewLine() {
    // IDEA-107383
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_ALIGN_PARAM_COMMENTS = true
    }

    doClassTest(
      """/**
 @param protocolId protocol id
 @param connectedUserIdHandlerFromServer user id
 @return
 */
public void register(int protocolId, int connectedUserIdHandlerFromServer) {
}""",

      """/**
 * @param protocolId                       protocol id
 * @param connectedUserIdHandlerFromServer user id
 * @return
 */
public void register(int protocolId, int connectedUserIdHandlerFromServer) {
}""")
  }

  fun testWrappedParameterDescription() {
    // Inspired by IDEA-13072
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_PARAM_DESCRIPTION_ON_NEW_LINE = true
    }
    settings.WRAP_COMMENTS = true

    doClassTest(
      """/**
 * test description
 * @param first first description
 * @param second
 * @param third third
 *              description
 * @param forth
 *          forth description
 */
void test(int first, int second, int third, int forth) {
}""",

      """/**
 * test description
 *
 * @param first
 *         first description
 * @param second
 * @param third
 *         third description
 * @param forth
 *         forth description
 */
void test(int first, int second, int third, int forth) {
}""")
  }

  fun testExceptionAlignmentCorrect() {
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_ALIGN_EXCEPTION_COMMENTS = true
    }

    doTextTest(
      """public class Controller {

    /**
     * @throws NoSearchServersConfiguredException If no search engine servers are configured.
     * @throws SearchServerUnavailableException If the search engine server is not accessible.
     * @throws InvalidSearchServerResponseException If the search engine server response was invalid.
     * @throws NotificationEncodingException If the request could not be encoded to UTF-8.
     * @throws NotificationUnavailableException If the notification server is not available or sent back an invalid response code.
     */
    public int superDangerousMethod() {
        return 68;
    }
}""",

      """public class Controller {

    /**
     * @throws NoSearchServersConfiguredException   If no search engine servers are configured.
     * @throws SearchServerUnavailableException     If the search engine server is not accessible.
     * @throws InvalidSearchServerResponseException If the search engine server response was invalid.
     * @throws NotificationEncodingException        If the request could not be encoded to UTF-8.
     * @throws NotificationUnavailableException     If the notification server is not available or sent back an invalid response code.
     */
    public int superDangerousMethod() {
        return 68;
    }
}""")
  }

  fun testDoNotWrapMultiLineCommentIntoOneLine() {
    javaSettings.apply{
      ENABLE_JAVADOC_FORMATTING = true
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
    }

    val test = """/**
 * foo
 */
public Object next() {
    return new Object();
}"""
    doClassTest(test, test)
  }

  fun testLeaveOneLineComment() {
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
    }

    val test = """/** foo */
public Object next() {
    return new Object();
}"""
    doClassTest(test, test)
  }

  fun testWrapOneLineComment() {
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false
    }

    doClassTest(
      """/** foo */
public Object next() {
    return new Object();
}""",

      """/**
 * foo
 */
public Object next() {
    return new Object();
}""")
  }

  fun testWrapStrangeComment() {
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = false
    }

    doClassTest(
      """/** foo */
public Object next() {
    return new Object();
}""",

      """/**
 * foo
 */
public Object next() {
    return new Object();
}""")
  }

  fun testWrapStrangeCommentIfNotWrapOneLines() {
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
    }
    doClassTest(
      """/** foo
 */public Object next() {
    return new Object();
}""",

      """/**
 * foo
 */
public Object next() {
    return new Object();
}""")
  }

  fun testReturnTagAlignment() {
    settings.apply {
      RIGHT_MARGIN = 80
      WRAP_COMMENTS = true
      WRAP_LONG_LINES = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doClassTest(
      """    /**
     * @return this is a return value documentation with a very long description that is longer than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters, which should be greater than any sane right margin.
     */
    public int method(int parameter) {
        return 0;
    }
""",

      """/**
 * @return this is a return value documentation with a very long description
 * that is longer than the right margin. It is more than 200 characters
 * long, not including the comment indent and the asterisk characters, which
 * should be greater than any sane right margin.
 */
public int method(int parameter) {
    return 0;
}
""")
  }

  fun testReturnTagAlignmentWithPreTagOnFirstLine() {
    settings.apply {
      RIGHT_MARGIN = 80
      WRAP_COMMENTS = true
      WRAP_LONG_LINES = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doClassTest(
      """
      /**
       * @return <pre>this is a return value documentation with a very long description
       * that is longer than the right margin.</pre>
       */
      public int method(int parameter) {
          return 0;
      }""".trimIndent(),

      """
      /**
       * @return <pre>this is a return value documentation with a very long description
       * that is longer than the right margin.</pre>
       */
      public int method(int parameter) {
          return 0;
      }""".trimIndent())
  }

  fun testDoNotMergeCommentLines() {
    settings.apply {
      WRAP_COMMENTS = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_PRESERVE_LINE_FEEDS = true
    }

    doClassTest(
      """/**
 * Some comment
 * 2016
 * Date: Mar 03, 2016
 *
 */
     public class TestCase {
}""",

      """/**
 * Some comment
 * 2016
 * Date: Mar 03, 2016
 */
public class TestCase {
}""")
  }

  fun testSeeTagAlignment() {
    settings.apply {
      RIGHT_MARGIN = 80
      WRAP_COMMENTS = true
      WRAP_LONG_LINES = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doClassTest(
      """    /**
     * @see this is an additional documentation with a very long description that is longer than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters which should be greater than any sane right margin
     */
    public int method(int parameter) {
        return 0;
    }""",

      """/**
 * @see this is an additional documentation with a very long description
 * that is longer than the right margin. It is more than 200 characters
 * long, not including the comment indent and the asterisk characters which
 * should be greater than any sane right margin
 */
public int method(int parameter) {
    return 0;
}""")
  }

  fun testDummySinceTagAlignment() {
    settings.apply {
      RIGHT_MARGIN = 80
      WRAP_COMMENTS = true
      WRAP_LONG_LINES = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doClassTest(
      """    /**
     * @since this is an additional documentation with a very long description that is longer than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters which should be greater than any sane right margin
     */
    public int method(int parameter) {
        return 0;
    }""",

      """/**
 * @since this is an additional documentation with a very long description
 * that is longer than the right margin. It is more than 200 characters
 * long, not including the comment indent and the asterisk characters which
 * should be greater than any sane right margin
 */
public int method(int parameter) {
    return 0;
}""")
  }

  fun testDummyDeprecatedTagAlignment() {
    settings.apply {
      RIGHT_MARGIN = 80
      WRAP_COMMENTS = true
      WRAP_LONG_LINES = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doClassTest(
      """    /**
     * @deprecated this is an additional documentation with a very long description that is longer than the right margin. It is more than 200 characters long, not including the comment indent and the asterisk characters which should be greater than any sane right margin
     */
    public int method(int parameter) {
        return 0;
    }""",

      """/**
 * @deprecated this is an additional documentation with a very long
 * description that is longer than the right margin. It is more than 200
 * characters long, not including the comment indent and the asterisk
 * characters which should be greater than any sane right margin
 */
public int method(int parameter) {
    return 0;
}""")
  }

  fun testJavadocFormattingIndependentOfMethodIndentation() {
    settings.apply {
      RIGHT_MARGIN = 50
      WRAP_COMMENTS = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
      JD_P_AT_EMPTY_LINES = false
      JD_KEEP_EMPTY_LINES = false
      JD_ADD_BLANK_AFTER_DESCRIPTION = false
    }

    formatEveryoneAndCheckIfResultEqual(
      """class A {
    /**
     * Some really great independent test approach purpose live fish
     * banana split string be accurate when writing tests and code
     * read write buffer.
     *
     * Some text after empty line
     *
     */
void foo() {

}
}""",

      """class A {
    /**
     * Some really great independent test approach purpose live fish
     * banana split string be accurate when writing tests and code
     * read write buffer.
     *
     * Some text after empty line
     *
     */
    void foo() {

    }
}""")
  }

  fun testJavadocAlignmentForInnerClasses() {
    settings.apply {
      RIGHT_MARGIN = 40
      WRAP_COMMENTS = true
    }
    javaSettings.apply {
      ENABLE_JAVADOC_FORMATTING = true
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }

    doTextTest(
      """public class Outer {
    class Inner {
        /**
         * Password from wild forest big house
         */
        public int getMagic() {
            return 312;
        }

class InnerInner {
/**
 * Special magic needs special rules
 */
public int innerMagic() {
    return 1;
}
}
    }
}""",

      """public class Outer {
    class Inner {
        /**
         * Password from wild forest big
         * house
         */
        public int getMagic() {
            return 312;
        }

        class InnerInner {
            /**
             * Special magic needs
             * special rules
             */
            public int innerMagic() {
                return 1;
            }
        }
    }
}""")
  }

  fun testAlignmentWithNoTopClassMembersIndentation() {
    settings.apply {
      RIGHT_MARGIN = 40
      WRAP_COMMENTS = true
      DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true
    }
    javaSettings.apply {
      JD_LEADING_ASTERISKS_ARE_ENABLED = true
    }
    doTextTest(
      """public class Outer {
class Inner {
/**
 * Password from wild forest big
 * house
 */
public int getMagic() {
    return 312;
}

class InnerInner {
/**
 * Special magic needs special rules
 */
public int innerMagic() {
    return 1;
}

class InnerInnerInner {
int iii;
class TripleInner {
int ti;
}
}
}
}
    public static void main(String[] args) {
        System.out.println("AAA!");
    }
}""",

      """public class Outer {
class Inner {
    /**
     * Password from wild forest big
     * house
     */
    public int getMagic() {
        return 312;
    }

    class InnerInner {
        /**
         * Special magic needs special
         * rules
         */
        public int innerMagic() {
            return 1;
        }

        class InnerInnerInner {
            int iii;

            class TripleInner {
                int ti;
            }
        }
    }
}

public static void main(String[] args) {
    System.out.println("AAA!");
}
}""")
  }

  fun testDoNotWrapLongLineCommentWithSpaceInStart() {
    settings.apply {
      KEEP_FIRST_COLUMN_COMMENT = true
      WRAP_LONG_LINES = true
      RIGHT_MARGIN = 200
    }

    val test = """public class JiraIssue {

    public static void main(String[] args) {
// AAAMIIGgIBADANBgkqhkiG9w0BAQEFAASCBugwgsdfssdflkldkflskdfsdkfjskdlfjdskjfksdjfksdjfkjsdkfjsdkfjgbkAgEAAoIBgQCZfKds4XjFWIU8D4OqCYJ0TkAkKPVV96v2l6PuMBNbON3ndHCVvwoJOJnopfbtFro9eCTCUC9MlAUZBAVdCbPVi3ioqaEN
    }
}"""
    doTextTest(test, test)
  }

  fun testNotGenerateSelfClosingPTagIfLanguageLevelJava8() {
    javaSettings.apply {
      JD_P_AT_EMPTY_LINES = true
      ENABLE_JAVADOC_FORMATTING = true
    }

    doClassTest(
      """/**
 * Super method
 *
 * Super multiple times
 */
public void voo() {
}
""",

      """/**
 * Super method
 * <p>
 * Super multiple times
 */
public void voo() {
}
""")
  }

  fun testPTagIfLanguageLevelNotJava8() {
    javaSettings.apply {
      JD_P_AT_EMPTY_LINES = true
      ENABLE_JAVADOC_FORMATTING = true
    }
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_7

    doClassTest(
      """/**
 * Super method
 *
 * Super multiple times
 */
public void voo() {
}
""",

      """/**
 * Super method
 * <p>
 * Super multiple times
 */
public void voo() {
}
""")
  }

  fun testDoNotTouchSingleLineComments() {
    javaSettings.apply {
      JD_DO_NOT_WRAP_ONE_LINE_COMMENTS = true
      ENABLE_JAVADOC_FORMATTING = true
    }

    doClassTest(
      """/****** AAAAAAA *******/

  public void t() {
         }""",

      """/****** AAAAAAA *******/

public void t() {
}""")
  }

  fun testKeepPTags() {
    javaSettings.apply {
      JD_P_AT_EMPTY_LINES = true
      ENABLE_JAVADOC_FORMATTING = true
    }

    doClassTest(
      """/**
 * <pre>new
 * code</pre>
 * <p>
 * Whatever.
 * <p>
 * Whatever.
    */
public static void main(String[] args) {
     }""",

      """/**
 * <pre>new
 * code</pre>
 * <p>
 * Whatever.
 * <p>
 * Whatever.
 */
public static void main(String[] args) {
}""")
  }

  fun testTouchNothingInsidePreTag() {
    doClassTest(
      """/**
 *   Holla
 * <pre>
 * @Override
 *              Test me
 * </pre>
 */
public void test() {
}""",

      """/**
 * Holla
 * <pre>
 * @Override
 *              Test me
 * </pre>
 */
public void test() {
}""")
  }

  fun testContinuationDescriptionFormatting() {
    currentCodeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, 40)
    currentCodeStyleSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    javaSettings.JD_INDENT_ON_CONTINUATION = true
    javaSettings.JD_ALIGN_PARAM_COMMENTS = false
    javaSettings.JD_ALIGN_EXCEPTION_COMMENTS = false
    settings.WRAP_COMMENTS = true

    doClassTest(
      """/**
 * Just some random text
 * @param aParameter randomness in life does not mean it's easy to generate random text
 * @param bParameter another random parameter with qualified epoch
 * @author rumor oculus rivierra underground sound
 * @myrandomtag just write what you want and cranberries with bicycle
 * @return super string with everything involved, be aware
 */
String test(int aParameter, int bParameter) {
  return "";
}
""",

      """/**
 * Just some random text
 *
 * @param aParameter randomness in
 *   life does not mean it's easy to
 *   generate random text
 * @param bParameter another
 *   random parameter with qualified
 *   epoch
 * @return super string with
 *   everything involved, be aware
 * @author rumor oculus rivierra
 *   underground sound
 * @myrandomtag just write what
 *   you want and cranberries with
 *   bicycle
 */
String test(int aParameter, int bParameter) {
    return "";
}
""")
  }

  fun testJavadocWithTabs() {
    doClassTest(
      "\t/**\n" +
      "\t \t *\n" +
      "\t \t *\n" +
      "\t \t */\n" +
      "\tvoid check() {\n" +
      "\t}",

      "/**\n" +
      " *\n" +
      " */\n" +
      "void check() {\n" +
      "}")
  }

  fun testMultipleSince() {
    doTextTest(
      """/**
 * @since 1.7
 * @since 2.0
 */
public class C {
}""",

      """/**
 * @since 1.7
 * @since 2.0
 */
public class C {
}""")
  }

  fun testModuleComment() {
    doTextTest(
      """/**
 * A module.
 * @uses SomeService
 */
module M {
}""",

      """/**
 * A module.
 *
 * @uses SomeService
 */
module M {
}""")
  }

  fun testRichHtml() {
    settings.apply {
      WRAP_COMMENTS = true
      RIGHT_MARGIN = 50
    }
    javaSettings.JD_ADD_BLANK_AFTER_DESCRIPTION = false
    doTextTest(
      """public class Test {
    /**
     * <h1>A description containing HTML tags</h1>
     * <p>
     * There might be lists in descriptions like this one:
     * <ul>
     *     <li>Item one</li>
     *     <li>Item two</li>
     *     <li>Item three</li>
     * </ul>
     * which should be left as is, without any tags merged.
     * </p>
     * @param a Parameter descriptions can also be long but tag
     *          content should be left intact:
     *          <ol>
     *              <li>Another item one</li>
     *              <li>Item two</li>
     *          </ol>
     */
    void test(int a) {
    }
}
""",

      """public class Test {
    /**
     * <h1>A description containing HTML
     * tags</h1>
     * <p>
     * There might be lists in descriptions like
     * this one:
     * <ul>
     *     <li>Item one</li>
     *     <li>Item two</li>
     *     <li>Item three</li>
     * </ul>
     * which should be left as is, without any tags merged.
     * </p>
     * @param a Parameter descriptions can also be
     *          long but tag content should be
     *          left intact:
     *          <ol>
     *              <li>Another item one</li>
     *              <li>Item two</li>
     *          </ol>
     */
    void test(int a) {
    }
}
"""
    )
  }

  /**
   * See [IDEA-153768](https://youtrack.jetbrains.com/issue/IDEA-153768)
   */
  fun testPTagsBeforeTags() {
    javaSettings.JD_P_AT_EMPTY_LINES = true
    doTextTest(
"""
package com.company;

public class Test {
    /**
     * Do not remove existing
     * <p>
     * <b>Some text</b>
     * Before title
     *
     * <h1>Title</h1>
     *
     * <p>Before another already existing tag
     *
     * Normal case, should be inserted.
     */
    void foo() {
    }
}
""",
"""
package com.company;

public class Test {
    /**
     * Do not remove existing
     * <p>
     * <b>Some text</b>
     * Before title
     *
     * <h1>Title</h1>
     *
     * <p>Before another already existing tag
     * <p>
     * Normal case, should be inserted.
     */
    void foo() {
    }
}
""")
  }

  /**
   * See [IDEA-21623](https://youtrack.jetbrains.com/issue/IDEA-21623)
   */
  fun testPreTagWithAttributes() {
    settings.apply {
      WRAP_COMMENTS = true
      RIGHT_MARGIN = 60
    }

    doTextTest(
"""
package com.company;

interface Test {
    /**
     * The sample is below:
     *
     * <pre class="sample">
     *     class Foo {
     *         private int x;
     *         private int y;
     *         // The comment inside pre tag which shouldn't be wrapped despite being long
     *     }
     * </pre>
     * @return Some value
     */
    String foo();
}
""",

"""
package com.company;

interface Test {
    /**
     * The sample is below:
     *
     * <pre class="sample">
     *     class Foo {
     *         private int x;
     *         private int y;
     *         // The comment inside pre tag which shouldn't be wrapped despite being long
     *     }
     * </pre>
     *
     * @return Some value
     */
    String foo();
}
"""
    )
  }


  fun testIdea175161() {
    settings.apply {
      RIGHT_MARGIN = 160
      WRAP_COMMENTS = true
      KEEP_LINE_BREAKS = false
    }

    doTextTest(
"""package com.company;

public class Test {

    /**
     * Shortcut method for EntryDto items. This method will fetch the id from {@code item} and pass it to
     * {@link #test2(Object, Object, Object, Object, Object)}. Make sure the dto item returns a non-null id.
     */
    public void test() {}

    private void test2(Object a, Object b, Object c, Object d, Object e) {}
}
""",

"""package com.company;

public class Test {

    /**
     * Shortcut method for EntryDto items. This method will fetch the id from {@code item} and pass it to
     * {@link #test2(Object, Object, Object, Object, Object)}. Make sure the dto item returns a non-null id.
     */
    public void test() {
    }

    private void test2(Object a, Object b, Object c, Object d, Object e) {
    }
}
"""
    )
  }

  fun testIdea180882() {
    javaSettings.apply {
      JD_KEEP_EMPTY_PARAMETER = false
    }
    doTextTest(
"""
public class Test {

    /**
     * @param a
     * @param b
     */
    public void foo(boolean a, boolean b) {

    }
}
""",

"""
public class Test {

    /**
     *
     */
    public void foo(boolean a, boolean b) {

    }
}
"""
    )
  }

  fun testIdea205110() {
    doTextTest(
      """
      package com.test;

      import java.util.HashMap;
      import java.util.Map;

      public class GeneralTest {

          /**
           * @return Map<String, string>
           */
          private Map<String, String> test() {
              return new HashMap<>();
          }

      }
      """.trimIndent(),

      """
      package com.test;

      import java.util.HashMap;
      import java.util.Map;

      public class GeneralTest {

          /**
           * @return Map<String, string>
           */
          private Map<String, String> test() {
              return new HashMap<>();
          }

      }
      """.trimIndent()
    )
  }

  fun testIdea147601() {
    doTextTest(
      """
      public class Idea147601 {
      /**
       * <table summary="">
       *     <thead>
       *         <tr>
       *             <td>ABC</td>
       *             <td>DEF</td>
       *         </tr>
       *     </thead>
       *     <tbody>
       *         <tr>
       *             <td>some data here</td>
       *             <td>some more</td>
       *         </tr>
       *     </tbody>
       * </table>
       */
      void docTest() {}
      }
      """.trimIndent(),

      """
      public class Idea147601 {
          /**
           * <table summary="">
           *     <thead>
           *         <tr>
           *             <td>ABC</td>
           *             <td>DEF</td>
           *         </tr>
           *     </thead>
           *     <tbody>
           *         <tr>
           *             <td>some data here</td>
           *             <td>some more</td>
           *         </tr>
           *     </tbody>
           * </table>
           */
          void docTest() {
          }
      }
      """.trimIndent()
    )
  }

  fun testIdea221827() {
    settings.apply {
      RIGHT_MARGIN = 40
      WRAP_LONG_LINES = true
    }

    doTextTest(
      """
      /**
       * <pre>
       *     ScheduledFuture<?> future = executor.scheduleAtFixedRate(runnable, interval + delta, interval + delta, MILLISECONDS);
       * </pre>
       */
      final class Temp { }
      """.trimIndent(),

      """
      /**
       * <pre>
       *     ScheduledFuture<?> future = executor.scheduleAtFixedRate(runnable, interval + delta, interval + delta, MILLISECONDS);
       * </pre>
       */
      final class Temp {
      }
      """.trimIndent()
    )
  }

  fun testIdea219194() {
    doTextTest(
      """
      public class Test {
          /**
           * @implNote Some leading implNote sentence.
           *
           *     <p>That means that the the thing is like this.
           *
           *     <ol></ol>
           */
          void foo() {
          }
      }
      """.trimIndent(),

      """
      public class Test {
          /**
           * @implNote Some leading implNote sentence.
           *
           * <p>That means that the the thing is like this.
           *
           * <ol></ol>
           */
          void foo() {
          }
      }
      """.trimIndent()
    )
  }

  fun testIdea186041() {
    doTextTest(
      """
      import java.util.function.Function;

      public class Test {
          /**
           * @param node the node to get the path for
           * @param getParent a function to get a parent node for the given one
           * @param converter  a function to convert path components
           * @return a tree path with the converted path components or {@code null}
           * if the specified collection is empty
           * or a path component is {@code null}
           * or a path component is converted to {@code null}
           * Returns the path from the root, to get to this node.  The last
           * element in the path is this node.
           *
           * @return an array of TreeNode objects giving the path, where the
           *         first element in the path is the root and the last
           *         element is this node.
           */
          public TreeNode[] foo(String node, Function<TreeNode,TreeNode> getParent, Function<String,String> converter) {
              return new TreeNode[0];
          }

          private static class TreeNode {}
      }
      """.trimIndent(),

      """
      import java.util.function.Function;

      public class Test {
          /**
           * @param node      the node to get the path for
           * @param getParent a function to get a parent node for the given one
           * @param converter a function to convert path components
           * @return a tree path with the converted path components or {@code null}
           * if the specified collection is empty
           * or a path component is {@code null}
           * or a path component is converted to {@code null}
           * Returns the path from the root, to get to this node.  The last
           * element in the path is this node.
           * @return an array of TreeNode objects giving the path, where the
           * first element in the path is the root and the last
           * element is this node.
           */
          public TreeNode[] foo(String node, Function<TreeNode, TreeNode> getParent, Function<String, String> converter) {
              return new TreeNode[0];
          }
      
          private static class TreeNode {
          }
      }
      """.trimIndent()
    )
  }

  fun testIdea198240() {
    val config = TodoConfiguration.getInstance()
    val currFlag = config.isMultiLine
    try {
      config.isMultiLine = true
      doTextTest(
        """
        public class Test {
            /**
             * TODO This is a long to do comment which
             *   takes multiple lines. Indentation of these
             *   lines should remain.
             *
             *   @param i Parameter
             */
            void foo(int i) { }
        }
        """.trimIndent(),

        """
        public class Test {
            /**
             * TODO This is a long to do comment which
             *   takes multiple lines. Indentation of these
             *   lines should remain.
             *
             * @param i Parameter
             */
            void foo(int i) {
            }
        }
        """.trimIndent()
      )
    }
    finally {
      config.isMultiLine = currFlag
    }
  }

  fun testIdea277973() {
    val config = TodoConfiguration.getInstance()
    val currFlag = config.isMultiLine
    try {
      config.isMultiLine = true
      doTextTest(
        """
        public class Test {
            /**
             * FIXME This is a long to do comment which
             *   takes multiple lines. Indentation of these
             *   lines should remain.
             *
             *   @param i Parameter
             */
            void foo(int i) { }
        }
        """.trimIndent(),

        """
        public class Test {
            /**
             * FIXME This is a long to do comment which
             *   takes multiple lines. Indentation of these
             *   lines should remain.
             *
             * @param i Parameter
             */
            void foo(int i) {
            }
        }
        """.trimIndent()
      )
    }
    finally {
      config.isMultiLine = currFlag
    }
  }

  fun testIdea122233() {
    settings.apply {
      RIGHT_MARGIN = 60
      WRAP_COMMENTS = true
    }
      doTextTest(
        """
        /**
         * object that can be loaded into a {@link javax.sound.midi.Synthesizer}.
         */
        public class Main {
        }
        """.trimIndent(),

        """
        /**
         * object that can be loaded into a
         * {@link javax.sound.midi.Synthesizer}.
         */
        public class Main {
        }
        """.trimIndent()
      )
  }

  fun testEmptyJavadoc() {
      doTextTest(
        """
        /**
          */
        public class Main {
        }
        """.trimIndent(),

        """
        /**
         *
         */
        public class Main {
        }
        """.trimIndent()
      )
  }
}