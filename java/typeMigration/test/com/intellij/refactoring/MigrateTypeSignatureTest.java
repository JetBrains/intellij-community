package com.intellij.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.TypeMigrationTestUtil.RulesProvider;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFieldType;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFirstParamType;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestMethodType;
import static com.intellij.refactoring.TypeMigrationTestUtil.start;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MigrateTypeSignatureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/migrateTypeSignature/");

  @Test
  public void testExprAccess2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myForAccess", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null)));
  }

  @Test
  public void testExprAccess2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassGrandChild", null)));
  }

  @Test
  public void testExprAccessParent2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myForSuperAccess", "Ession",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null)));
  }

  @Test
  public void testExprAccessParent2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassGrandChild", null)));
  }

  @Test
  public void testExprArrayAccessNegative() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.doubleType());
  }

  @Test
  public void testExprArrayAccessPositive() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.charType());
  }

  @Test
  public void testExprCalcBooleanBoolean() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.intType());
  }

  @Test
  public void testExprCalcBooleanNumeric() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testExprCalcBooleanReference() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         PsiTypes.doubleType());
  }

  @Test
  public void testExprCalcNumeric2Boolean() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.booleanType());
  }

  @Test
  public void testExprCalcNumeric2Floating() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.floatType());
  }

  @Test
  public void testExprCalcNumeric2Int() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.longType());
  }

  @Test
  public void testExprCalcNumeric2String() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprCast2LvalueNeg() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprCast2LvaluePos() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.intType());
  }

  @Test
  public void testExprConcatNumeric2Reference() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testExprConcatNumeric2String() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprConcatString2Numeric() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         PsiTypes.intType());
  }

  @Test
  public void testExprConcatString2Reference() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null)));
  }

  @Test
  public void testExprInstanceofNeg() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, null)));
  }

  @Test
  public void testExprInstanceofPos() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.AbstractMap", null)));
  }

  @Test
  public void testExprLiteralBoolean() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.booleanType());
  }

  @Test
  public void testExprLiteralByte() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.byteType());
  }

  @Test
  public void testExprLiteralChar() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.charType());
  }

  @Test
  public void testExprLiteralClassExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<? extends java.util.Collection[]>", null)));
  }

  @Test
  public void testExprLiteralClassPrimitive() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<Integer>", null)));
  }

  @Test
  public void testExprLiteralClassPrimitiveArray() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<int[]>", null)));
  }

  @Test
  public void testExprLiteralClassRaw() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class", null)));
  }

  @Test
  public void testExprLiteralClassReference() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<java.util.Set>", null)));
  }

  @Test
  public void testExprLiteralClassReferenceArray() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<java.util.Set[]>", null)));
  }

  @Test
  public void testExprLiteralClassSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Class<? super java.util.AbstractSet[]>", null)));
  }

  @Test
  public void testExprLiteralDouble() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.doubleType());
  }

  @Test
  public void testExprLiteralFloat() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.floatType());
  }

  @Test
  public void testExprLiteralInt() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.intType());
  }

  @Test
  public void testExprLiteralLong() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.longType());
  }

  @Test
  public void testExprLiteralShort() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.shortType());
  }

  @Test
  public void testExprLiteralString() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprNewArrayArray2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("FaceParent", null).createArrayType()));
  }

  @Test
  public void testExprNewArrayArray2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null).createArrayType().createArrayType().createArrayType()));
  }

  @Test
  public void testExprNewArrayGen2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<java.lang.Integer>", null).createArrayType()));
  }

  @Test
  public void testExprNewArrayPrimitive2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Expr", PsiTypes.intType());
  }

  @Test
  public void testExprNewArrayPrimitive2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    PsiTypes.intType().createArrayType().createArrayType());
  }

  @Test
  public void testExprNewArrayReftype2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("FaceParent", null)));
  }

  @Test
  public void testExprNewArrayReftype2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null).createArrayType().createArrayType()));
  }

  @Test
  public void testExprNewGen() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<Subject>", null)));
  }

  @Test
  public void testExprNewGenExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? extends Subject>", null)));
  }

  @Test
  public void testExprNewGenSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? super Subject>", null)));
  }

  @Test
  public void testExprNewReference() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Expr",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Expr.Subject", null)));
  }

  @Test
  public void testExprReturn2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprReturn2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestMethodType(multiFile.get(), "meth", "Expr",
                     ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testExprTernary() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Expr",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testOverridingDown() {
    doTestMethodType(multiFile.get(), "getInt", "Parent", PsiTypes.byteType());
  }

  @Test
  public void testOverridingUp() {
    doTestMethodType(multiFile.get(), "getInt", "Child", PsiTypes.byteType());
  }

  @Test
  public void testSpecJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    doTestFirstParamType(multiFile.get(), "meth", "Spec",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null)));
  }

  @Test
  public void testSpecNotUsed() {
    doTestFieldType(multiFile.get(), "myField", "Spec", PsiTypes.booleanType());
  }

  @Test
  public void testTypeArrayReftype2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Subject", null).createArrayType()));
  }

  @Test
  public void testTypeArrayReftype2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Subject", null).createArrayType().createArrayType()));
  }

  @Test
  public void testTypeArrayRoots2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Holder", null).createArrayType()));
  }

  @Test
  public void testTypeArrayVararg2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         new PsiEllipsisType(ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Subject", null))));
  }

  @Test
  public void testTypeArrayVararg2RvalueNeg() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Descendant", null).createArrayType()));
  }

  @Test
  public void testTypeArrayVararg2RvaluePos() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("Subject", null).createArrayType()));
  }

  @Test
  public void testTypeAutoboxBoolean2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Boolean", null)));
  }

  @Test
  public void testTypeAutoboxBoolean2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.booleanType());
  }

  @Test
  public void testTypeAutoboxByte2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Byte", null)));
  }

  @Test
  public void testTypeAutoboxByte2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.byteType());
  }

  @Test
  public void testTypeAutoboxChar2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Character", null)));
  }

  @Test
  public void testTypeAutoboxChar2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.charType());
  }

  @Test
  public void testTypeAutoboxDouble2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Double", null)));
  }

  @Test
  public void testTypeAutoboxDouble2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.doubleType());
  }

  @Test
  public void testTypeAutoboxFloat2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Float", null)));
  }

  @Test
  public void testTypeAutoboxFloat2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.floatType());
  }

  @Test
  public void testTypeAutoboxInt2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Integer", null)));
  }

  @Test
  public void testTypeAutoboxInt2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.intType());
  }

  @Test
  public void testTypeAutoboxLong2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Long", null)));
  }

  @Test
  public void testTypeAutoboxLong2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.longType());
  }

  @Test
  public void testTypeAutoboxShort2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Short", null)));
  }

  @Test
  public void testTypeAutoboxShort2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type",
                    PsiTypes.shortType());
  }

  @Test
  public void testTypeGenAncestor2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<Subject>", null)));
  }

  @Test
  public void testTypeGenAncestorWildcard2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? extends Subject>", null)));
  }

  @Test
  public void testTypeGenDescendant2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<Subject>", null)));
  }

  @Test
  public void testTypeGenDescendantWildcard2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? super Subject>", null)));
  }

  @Test
  public void testTypeGenRaw2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null)));
  }

  @Test
  public void testTypeGenRaw2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null)));
  }

  @Test
  public void testTypePrimsubBoolean2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.booleanType());
  }

  @Test
  public void testTypePrimsubBoolean2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.booleanType());
  }

  @Test
  public void testTypePrimsubByte2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.byteType());
  }

  @Test
  public void testTypePrimsubChar2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.charType());
  }

  @Test
  public void testTypePrimsubChar2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.charType());
  }

  @Test
  public void testTypePrimsubDouble2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.doubleType());
  }

  @Test
  public void testTypePrimsubFloat2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.floatType());
  }

  @Test
  public void testTypePrimsubFloat2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.floatType());
  }

  @Test
  public void testTypePrimsubInt2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.intType());
  }

  @Test
  public void testTypePrimsubInt2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.intType());
  }

  @Test
  public void testTypePrimsubLong2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.longType());
  }

  @Test
  public void testTypePrimsubLong2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.longType());
  }

  @Test
  public void testTypePrimsubShort2Lvalue() {
    doTestFirstParamType(multiFile.get(), "meth", "Type", PsiTypes.shortType());
  }

  @Test
  public void testTypePrimsubShort2Rvalue() {
    doTestFieldType(multiFile.get(), "myField", "Type", PsiTypes.shortType());
  }

  @Test
  public void testTypeRefClassChild2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassChild", null)));
  }

  @Test
  public void testTypeRefClassParent2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null)));
  }

  @Test
  public void testTypeRefClassParent2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("ClassParent", null)));
  }

  @Test
  public void testTypeRefFaceChild2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("FaceChild", null)));
  }

  @Test
  public void testTypeRefFaceChild2Rvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFieldType(multiFile.get(), "myField", "Type",
                    ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("FaceChild", null)));
  }

  @Test
  public void testTypeRefFaceParent2Lvalue() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "meth", "Type",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("FaceParent", null)));
  }

  @Test
  public void testMigrateAnonymousClassTypeParameters() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestAnonymousClassMethod("invoke",
                               ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.String", null)));
  }

  @Test
  public void testMigrateAnonymousClassTypeParameters2() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestAnonymousClassMethod("invoke",
                               ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Long", null)));
  }

  protected void doTestAnonymousClassMethod(@NotNull final String methodName,
                                            final PsiType toType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) {
        return toType;
      }

      @Override
      public PsiElement victim(PsiClass aClass) {
        final PsiAnonymousClass anonymousClass = PsiTreeUtil.findChildOfType(aClass, PsiAnonymousClass.class);
        assertNotNull(anonymousClass);
        return anonymousClass.findMethodsByName(methodName, false)[0];
      }
    };
    start(multiFile.get(), provider);
  }
}
