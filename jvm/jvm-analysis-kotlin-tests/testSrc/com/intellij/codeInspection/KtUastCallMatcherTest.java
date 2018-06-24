package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import kotlin.KotlinVersion;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastCallKind;
import org.junit.Assume;

import java.io.File;
import java.util.Set;

import static com.intellij.codeInspection.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;
import static com.intellij.codeInspection.UastCallMatcher.builder;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/uastCallMatcher")
public class KtUastCallMatcherTest extends UastCallMatcherTestBase {
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
    super.tuneFixture(moduleBuilder);
    //TODO check if adding kotlin-stdlib is redundant
    String kotlinDir = PathManager.getHomePath().replace(File.separatorChar, '/') +
                       "/community/build/dependencies/build/kotlin/Kotlin/kotlinc/lib";
    moduleBuilder.addLibraryJars("kotlin-stdlib", kotlinDir, "kotlin-stdlib.jar");
  }


  public void testCallableReferences() {
    doTestCallableReferences("MethodReferences.kt");
  }

  public void testSimpleMatcher() {
    PsiFile file = myFixture.configureByFile("MyClass.kt");

    Set<UCallExpression> expressions = getUElementsOfTypeFromFile(file, UCallExpression.class,
                                                                  e -> e.getKind() == UastCallKind.METHOD_CALL);
    assertSize(5, expressions);

    assertEquals(1, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withMethodName("size").build(),
      expressions)
    );
    assertEquals(1, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentsCount(1).build(),
      expressions)
    );
    assertEquals(1, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentTypes("java.util.Collection").build(),
      expressions)
    );

    assertEquals(4, matchCallExpression(
      builder().withClassFqn("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withClassFqn("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));

    assertEquals(3, matchCallExpression(
      builder().withReturnType("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentsCount(1).build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentTypes("java.util.Locale").build(),
      expressions
    ));

    assertEquals(2, matchCallExpression(
      builder().withArgumentsCount(0).build(),
      expressions
    ));
    assertEquals(3, matchCallExpression(
      builder().withArgumentsCount(1).build(),
      expressions
    ));

    assertEquals(1, matchCallExpression(
      builder().withArgumentTypes("java.util.Locale").build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withArgumentTypes("java.util.Collection").withMatchArgumentTypeInheritors(true).build(),
      expressions
    ));
  }
}
