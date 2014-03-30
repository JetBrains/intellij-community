package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

abstract public class PostfixTemplateTestCase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + getSuffix();
  }

  @Override
  final protected String getTestDataPath() {
    return super.getTestDataPath();
  }

  @NotNull
  protected abstract String getSuffix();

  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package java.lang;\n" +
                       "public final class Boolean implements java.io.Serializable, Comparable<Boolean> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public final class Byte implements java.io.Serializable, Comparable<Byte> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public interface Iterable<T> {}");
    myFixture.addClass("package java.util;\n" +
                       "public class ArrayList<E> extends AbstractList<E>\n" +
                       "        implements List<E>, Iterable<E>, RandomAccess, Cloneable, java.io.Serializable {}");
  }
}
