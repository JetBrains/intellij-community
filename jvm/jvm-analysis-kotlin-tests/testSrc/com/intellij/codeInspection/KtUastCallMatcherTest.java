package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import kotlin.KotlinVersion;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastCallKind;
import org.junit.Assume;

import java.io.File;
import java.util.Locale;
import java.util.Set;

import static com.intellij.codeInspection.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;
import static com.intellij.codeInspection.UastCallMatcher.builder;

@SuppressWarnings("Duplicates") // TODO refactor once the tests pass
@TestDataPath("$CONTENT_ROOT/testData/codeInspection/uastCallMatcher")
public class KtUastCallMatcherTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    Assume.assumeTrue(KotlinVersion.CURRENT.isAtLeast(1, 2, 60));
    super.setUp();
  }

  @Override
  protected String getBasePath() {
    return JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/uastCallMatcher";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(Locale.class));
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
    String kotlinDir = PathManager.getHomePath().replace(File.separatorChar, '/') +
                       "/community/build/dependencies/build/kotlin/Kotlin/kotlinc/lib";
    moduleBuilder.addLibraryJars("kotlin-stdlib", kotlinDir, "kotlin-stdlib.jar");
  }



  public void testSimpleMatcher() {
    PsiFile file = myFixture.configureByFile("MyClass.kt");

    Set<UCallExpression> expressions = getUElementsOfTypeFromFile(file, UCallExpression.class,
                                                                  e -> e.getKind() == UastCallKind.METHOD_CALL);
    assertSize(5, expressions);

    assertEquals(1, match(
      builder().withClassFqn("java.util.ArrayList").build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withClassFqn("java.util.ArrayList").withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withMethodName("size").build(),
      expressions)
    );
    assertEquals(1, match(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentsCount(1).build(),
      expressions)
    );
    assertEquals(1, match(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentTypes("java.util.Collection").build(),
      expressions)
    );

    assertEquals(4, match(
      builder().withClassFqn("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withClassFqn("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));

    assertEquals(3, match(
      builder().withReturnType("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentsCount(1).build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentTypes("java.util.Locale").build(),
      expressions
    ));

    assertEquals(2, match(
      builder().withArgumentsCount(0).build(),
      expressions
    ));
    assertEquals(3, match(
      builder().withArgumentsCount(1).build(),
      expressions
    ));

    assertEquals(1, match(
      builder().withArgumentTypes("java.util.Locale").build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withArgumentTypes("java.util.Collection").withMatchArgumentTypeInheritors(true).build(),
      expressions
    ));
  }

  private static int match(UastCallMatcher matcher, Set<UCallExpression> expressions) {
    return (int)expressions.stream().filter(e -> matcher.testCallExpression(e)).count();
  }
}
