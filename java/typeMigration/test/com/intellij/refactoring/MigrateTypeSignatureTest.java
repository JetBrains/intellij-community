package com.intellij.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class MigrateTypeSignatureTest extends TypeMigrationTestBase {
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/migrateTypeSignature/";
  }

  public void testExprAccess2Lvalue() {
    doTestFieldType("myForAccess", "Expr",
                    getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccess2Rvalue() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprAccessParent2Lvalue() {
    doTestFieldType("myForSuperAccess", "Ession",
                    getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccessParent2Rvalue() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprArrayAccessNegative() {
    doTestFirstParamType("meth", "Expr", PsiTypes.doubleType());
  }

  public void testExprArrayAccessPositive() {
    doTestFirstParamType("meth", "Expr", PsiTypes.charType());
  }

  public void testExprCalcBooleanBoolean() {
    doTestFirstParamType("meth", "Expr", PsiTypes.intType());
  }

  public void testExprCalcBooleanNumeric() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprCalcBooleanReference() {
    doTestFirstParamType("meth", "Expr",
                         PsiTypes.doubleType());
  }

  public void testExprCalcNumeric2Boolean() {
    doTestFirstParamType("meth", "Expr", PsiTypes.booleanType());
  }

  public void testExprCalcNumeric2Floating() {
    doTestFirstParamType("meth", "Expr", PsiTypes.floatType());
  }

  public void testExprCalcNumeric2Int() {
    doTestFirstParamType("meth", "Expr", PsiTypes.longType());
  }

  public void testExprCalcNumeric2String() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvalueNeg() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvaluePos() {
    doTestFirstParamType("meth", "Expr", PsiTypes.intType());
  }

  public void testExprConcatNumeric2Reference() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprConcatNumeric2String() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprConcatString2Numeric() {
    doTestFirstParamType("meth", "Expr",
                         PsiTypes.intType());
  }

  public void testExprConcatString2Reference() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprInstanceofNeg() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, null));
  }

  public void testExprInstanceofPos() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.util.AbstractMap", null));
  }

  public void testExprLiteralBoolean() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.booleanType());
  }

  public void testExprLiteralByte() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.byteType());
  }

  public void testExprLiteralChar() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.charType());
  }

  public void testExprLiteralClassExtends() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<? extends java.util.Collection[]>", null));
  }

  public void testExprLiteralClassPrimitive() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<Integer>", null));
  }

  public void testExprLiteralClassPrimitiveArray() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<int[]>", null));
  }

  public void testExprLiteralClassRaw() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class", null));
  }

  public void testExprLiteralClassReference() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<java.util.Set>", null));
  }

  public void testExprLiteralClassReferenceArray() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<java.util.Set[]>", null));
  }

  public void testExprLiteralClassSuper() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.Class<? super java.util.AbstractSet[]>", null));
  }

  public void testExprLiteralDouble() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.doubleType());
  }

  public void testExprLiteralFloat() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.floatType());
  }

  public void testExprLiteralInt() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.intType());
  }

  public void testExprLiteralLong() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.longType());
  }

  public void testExprLiteralShort() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.shortType());
  }

  public void testExprLiteralString() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprNewArrayArray2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("FaceParent", null).createArrayType());
  }

  public void testExprNewArrayArray2Rvalue() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType().createArrayType());
  }

  public void testExprNewArrayGen2Rvalue() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.util.Set<java.lang.Integer>", null).createArrayType());
  }

  public void testExprNewArrayPrimitive2Lvalue() {
    doTestFirstParamType("meth", "Expr", PsiTypes.intType());
  }

  public void testExprNewArrayPrimitive2Rvalue() {
    doTestFieldType("myField", "Expr",
                    PsiTypes.intType().createArrayType().createArrayType());
  }

  public void testExprNewArrayReftype2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("FaceParent", null));
  }

  public void testExprNewArrayReftype2Rvalue() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType());
  }

  public void testExprNewGen() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testExprNewGenExtends() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testExprNewGenSuper() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testExprNewReference() {
    doTestFieldType("myField", "Expr",
                    getElementFactory().createTypeFromText("Expr.Subject", null));
  }

  public void testExprReturn2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprReturn2Rvalue() {
    doTestMethodType("meth", "Expr",
                     getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprTernary() {
    doTestFirstParamType("meth", "Expr",
                         getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testOverridingDown() {
    doTestMethodType("getInt", "Parent", PsiTypes.byteType());
  }

  public void testOverridingUp() {
    doTestMethodType("getInt", "Child", PsiTypes.byteType());
  }

  public void testSpecJavadoc() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    doTestFirstParamType("meth", "Spec",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testSpecNotUsed() {
    doTestFieldType("myField", "Spec", PsiTypes.booleanType());
  }

  public void testTypeArrayReftype2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeArrayReftype2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("Subject", null).createArrayType().createArrayType());
  }

  public void testTypeArrayRoots2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("Holder", null).createArrayType());
  }

  public void testTypeArrayVararg2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         new PsiEllipsisType(getElementFactory().createTypeFromText("Subject", null)));
  }

  public void testTypeArrayVararg2RvalueNeg() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("Descendant", null).createArrayType());
  }

  public void testTypeArrayVararg2RvaluePos() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeAutoboxBoolean2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Boolean", null));
  }

  public void testTypeAutoboxBoolean2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.booleanType());
  }

  public void testTypeAutoboxByte2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Byte", null));
  }

  public void testTypeAutoboxByte2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.byteType());
  }

  public void testTypeAutoboxChar2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Character", null));
  }

  public void testTypeAutoboxChar2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.charType());
  }

  public void testTypeAutoboxDouble2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Double", null));
  }

  public void testTypeAutoboxDouble2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.doubleType());
  }

  public void testTypeAutoboxFloat2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Float", null));
  }

  public void testTypeAutoboxFloat2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.floatType());
  }

  public void testTypeAutoboxInt2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Integer", null));
  }

  public void testTypeAutoboxInt2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.intType());
  }

  public void testTypeAutoboxLong2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Long", null));
  }

  public void testTypeAutoboxLong2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.longType());
  }

  public void testTypeAutoboxShort2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.lang.Short", null));
  }

  public void testTypeAutoboxShort2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiTypes.shortType());
  }

  public void testTypeGenAncestor2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenAncestorWildcard2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testTypeGenDescendant2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenDescendantWildcard2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testTypeGenRaw2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypeGenRaw2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypePrimsubBoolean2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.booleanType());
  }

  public void testTypePrimsubBoolean2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.booleanType());
  }

  public void testTypePrimsubByte2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.byteType());
  }

  public void testTypePrimsubChar2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.charType());
  }

  public void testTypePrimsubChar2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.charType());
  }

  public void testTypePrimsubDouble2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.doubleType());
  }

  public void testTypePrimsubFloat2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.floatType());
  }

  public void testTypePrimsubFloat2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.floatType());
  }

  public void testTypePrimsubInt2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.intType());
  }

  public void testTypePrimsubInt2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.intType());
  }

  public void testTypePrimsubLong2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.longType());
  }

  public void testTypePrimsubLong2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.longType());
  }

  public void testTypePrimsubShort2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiTypes.shortType());
  }

  public void testTypePrimsubShort2Rvalue() {
    doTestFieldType("myField", "Type", PsiTypes.shortType());
  }

  public void testTypeRefClassChild2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("ClassChild", null));
  }

  public void testTypeRefClassParent2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefClassParent2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefFaceChild2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceChild2Rvalue() {
    doTestFieldType("myField", "Type",
                    getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceParent2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         getElementFactory().createTypeFromText("FaceParent", null));
  }

  public void testMigrateAnonymousClassTypeParameters() {
    doTestAnonymousClassMethod("invoke",
                               getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testMigrateAnonymousClassTypeParameters2() {
    doTestAnonymousClassMethod("invoke",
                               getElementFactory().createTypeFromText("java.lang.Long", null));
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
    start(provider);
  }
}
