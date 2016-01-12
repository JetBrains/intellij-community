/*
 * User: anna
 * Date: 30-Apr-2008
 */
package com.intellij.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class MigrateTypeSignatureTest extends TypeMigrationTestBase {
  @NotNull
  @Override
  public String getTestRoot() {
    return "/refactoring/migrateTypeSignature/";
  }

  public void testExprAccess2Lvalue() throws Exception {
    doTestFieldType("myForAccess", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccess2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprAccessParent2Lvalue() throws Exception {
    doTestFieldType("myForSuperAccess", "Ession",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccessParent2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprArrayAccessNegative() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.DOUBLE);
  }

  public void testExprArrayAccessPositive() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.CHAR);
  }

  public void testExprCalcBooleanBoolean() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprCalcBooleanNumeric() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprCalcBooleanReference() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         PsiType.DOUBLE);
  }

  public void testExprCalcNumeric2Boolean() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.BOOLEAN);
  }

  public void testExprCalcNumeric2Floating() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.FLOAT);
  }

  public void testExprCalcNumeric2Int() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.LONG);
  }

  public void testExprCalcNumeric2String() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvalueNeg() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvaluePos() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprConcatNumeric2Reference() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprConcatNumeric2String() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprConcatString2Numeric() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         PsiType.INT);
  }

  public void testExprConcatString2Reference() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprInstanceofNeg() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, null));
  }

  public void testExprInstanceofPos() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.AbstractMap", null));
  }

  public void testExprLiteralBoolean() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.BOOLEAN);
  }

  public void testExprLiteralByte() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.BYTE);
  }

  public void testExprLiteralChar() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.CHAR);
  }

  public void testExprLiteralClassExtends() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<? extends java.util.Collection[]>", null));
  }

  public void testExprLiteralClassPrimitive() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<Integer>", null));
  }

  public void testExprLiteralClassPrimitiveArray() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<int[]>", null));
  }

  public void testExprLiteralClassRaw() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class", null));
  }

  public void testExprLiteralClassReference() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<java.util.Set>", null));
  }

  public void testExprLiteralClassReferenceArray() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<java.util.Set[]>", null));
  }

  public void testExprLiteralClassSuper() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<? super java.util.AbstractSet[]>", null));
  }

  public void testExprLiteralDouble() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.DOUBLE);
  }

  public void testExprLiteralFloat() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.FLOAT);
  }

  public void testExprLiteralInt() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.INT);
  }

  public void testExprLiteralLong() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.LONG);
  }

  public void testExprLiteralShort() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.SHORT);
  }

  public void testExprLiteralString() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprNewArrayArray2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceParent", null).createArrayType());
  }

  public void testExprNewArrayArray2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType().createArrayType());
  }

  public void testExprNewArrayGen2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.lang.Integer>", null).createArrayType());
  }

  public void testExprNewArrayPrimitive2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprNewArrayPrimitive2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    PsiType.INT.createArrayType().createArrayType());
  }

  public void testExprNewArrayReftype2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceParent", null));
  }

  public void testExprNewArrayReftype2Rvalue() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType());
  }

  public void testExprNewGen() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testExprNewGenExtends() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testExprNewGenSuper() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testExprNewReference() throws Exception {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("Expr.Subject", null));
  }

  public void testExprReturn2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprReturn2Rvalue() throws Exception {
    doTestMethodType("meth", "Expr",
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprTernary() throws Exception {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testOverridingDown() throws Exception {
    doTestMethodType("getInt", "Parent", PsiType.BYTE);
  }

  public void testOverridingUp() throws Exception {
    doTestMethodType("getInt", "Child", PsiType.BYTE);
  }

  public void testSpecJavadoc() throws Exception {
    JavaCodeStyleSettings settings = getCurrentCodeStyleSettings().getCustomSettings(JavaCodeStyleSettings.class);
    settings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    doTestFirstParamType("meth", "Spec",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testSpecNotUsed() throws Exception {
    doTestFieldType("myField", "Spec", PsiType.BOOLEAN);
  }

  public void testTypeArrayReftype2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeArrayReftype2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType().createArrayType());
  }

  public void testTypeArrayRoots2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("Holder", null).createArrayType());
  }

  public void testTypeArrayVararg2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         new PsiEllipsisType(myJavaFacade.getElementFactory().createTypeFromText("Subject", null)));
  }

  public void testTypeArrayVararg2RvalueNeg() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Descendant", null).createArrayType());
  }

  public void testTypeArrayVararg2RvaluePos() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeAutoboxBoolean2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Boolean", null));
  }

  public void testTypeAutoboxBoolean2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.BOOLEAN);
  }

  public void testTypeAutoboxByte2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Byte", null));
  }

  public void testTypeAutoboxByte2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.BYTE);
  }

  public void testTypeAutoboxChar2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Character", null));
  }

  public void testTypeAutoboxChar2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.CHAR);
  }

  public void testTypeAutoboxDouble2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Double", null));
  }

  public void testTypeAutoboxDouble2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.DOUBLE);
  }

  public void testTypeAutoboxFloat2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Float", null));
  }

  public void testTypeAutoboxFloat2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.FLOAT);
  }

  public void testTypeAutoboxInt2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null));
  }

  public void testTypeAutoboxInt2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.INT);
  }

  public void testTypeAutoboxLong2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Long", null));
  }

  public void testTypeAutoboxLong2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.LONG);
  }

  public void testTypeAutoboxShort2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Short", null));
  }

  public void testTypeAutoboxShort2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    PsiType.SHORT);
  }

  public void testTypeGenAncestor2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenAncestorWildcard2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testTypeGenDescendant2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenDescendantWildcard2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testTypeGenRaw2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypeGenRaw2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypePrimsubBoolean2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.BOOLEAN);
  }

  public void testTypePrimsubBoolean2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.BOOLEAN);
  }

  public void testTypePrimsubByte2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.BYTE);
  }

  public void testTypePrimsubChar2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.CHAR);
  }

  public void testTypePrimsubChar2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.CHAR);
  }

  public void testTypePrimsubDouble2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.DOUBLE);
  }

  public void testTypePrimsubFloat2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.FLOAT);
  }

  public void testTypePrimsubFloat2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.FLOAT);
  }

  public void testTypePrimsubInt2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.INT);
  }

  public void testTypePrimsubInt2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.INT);
  }

  public void testTypePrimsubLong2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.LONG);
  }

  public void testTypePrimsubLong2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.LONG);
  }

  public void testTypePrimsubShort2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type", PsiType.SHORT);
  }

  public void testTypePrimsubShort2Rvalue() throws Exception {
    doTestFieldType("myField", "Type", PsiType.SHORT);
  }

  public void testTypeRefClassChild2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassChild", null));
  }

  public void testTypeRefClassParent2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefClassParent2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefFaceChild2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceChild2Rvalue() throws Exception {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceParent2Lvalue() throws Exception {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceParent", null));
  }

  public void testMigrateAnonymousClassTypeParameters() {
    doTestAnonymousClassMethod("invoke",
                               myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testMigrateAnonymousClassTypeParameters2() {
    doTestAnonymousClassMethod("invoke",
                               myJavaFacade.getElementFactory().createTypeFromText("java.lang.Long", null));
  }

  protected void doTestAnonymousClassMethod(@NotNull final String methodName,
                                            final PsiType toType) {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public PsiType migrationType(PsiElement context) throws Exception {
        return toType;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        final PsiAnonymousClass anonymousClass = PsiTreeUtil.findChildOfType(aClass, PsiAnonymousClass.class);
        assertNotNull(anonymousClass);
        return anonymousClass.findMethodsByName(methodName, false)[0];
      }
    };
    start(provider);
  }
}
