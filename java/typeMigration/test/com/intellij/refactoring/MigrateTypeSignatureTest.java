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

  public void testExprAccess2Lvalue() {
    doTestFieldType("myForAccess", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccess2Rvalue() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprAccessParent2Lvalue() {
    doTestFieldType("myForSuperAccess", "Ession",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testExprAccessParent2Rvalue() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassGrandChild", null));
  }

  public void testExprArrayAccessNegative() {
    doTestFirstParamType("meth", "Expr", PsiType.DOUBLE);
  }

  public void testExprArrayAccessPositive() {
    doTestFirstParamType("meth", "Expr", PsiType.CHAR);
  }

  public void testExprCalcBooleanBoolean() {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprCalcBooleanNumeric() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprCalcBooleanReference() {
    doTestFirstParamType("meth", "Expr",
                         PsiType.DOUBLE);
  }

  public void testExprCalcNumeric2Boolean() {
    doTestFirstParamType("meth", "Expr", PsiType.BOOLEAN);
  }

  public void testExprCalcNumeric2Floating() {
    doTestFirstParamType("meth", "Expr", PsiType.FLOAT);
  }

  public void testExprCalcNumeric2Int() {
    doTestFirstParamType("meth", "Expr", PsiType.LONG);
  }

  public void testExprCalcNumeric2String() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvalueNeg() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprCast2LvaluePos() {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprConcatNumeric2Reference() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprConcatNumeric2String() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprConcatString2Numeric() {
    doTestFirstParamType("meth", "Expr",
                         PsiType.INT);
  }

  public void testExprConcatString2Reference() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null));
  }

  public void testExprInstanceofNeg() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, null));
  }

  public void testExprInstanceofPos() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.AbstractMap", null));
  }

  public void testExprLiteralBoolean() {
    doTestFieldType("myField", "Expr",
                    PsiType.BOOLEAN);
  }

  public void testExprLiteralByte() {
    doTestFieldType("myField", "Expr",
                    PsiType.BYTE);
  }

  public void testExprLiteralChar() {
    doTestFieldType("myField", "Expr",
                    PsiType.CHAR);
  }

  public void testExprLiteralClassExtends() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<? extends java.util.Collection[]>", null));
  }

  public void testExprLiteralClassPrimitive() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<Integer>", null));
  }

  public void testExprLiteralClassPrimitiveArray() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<int[]>", null));
  }

  public void testExprLiteralClassRaw() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class", null));
  }

  public void testExprLiteralClassReference() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<java.util.Set>", null));
  }

  public void testExprLiteralClassReferenceArray() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<java.util.Set[]>", null));
  }

  public void testExprLiteralClassSuper() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.Class<? super java.util.AbstractSet[]>", null));
  }

  public void testExprLiteralDouble() {
    doTestFieldType("myField", "Expr",
                    PsiType.DOUBLE);
  }

  public void testExprLiteralFloat() {
    doTestFieldType("myField", "Expr",
                    PsiType.FLOAT);
  }

  public void testExprLiteralInt() {
    doTestFieldType("myField", "Expr",
                    PsiType.INT);
  }

  public void testExprLiteralLong() {
    doTestFieldType("myField", "Expr",
                    PsiType.LONG);
  }

  public void testExprLiteralShort() {
    doTestFieldType("myField", "Expr",
                    PsiType.SHORT);
  }

  public void testExprLiteralString() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprNewArrayArray2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceParent", null).createArrayType());
  }

  public void testExprNewArrayArray2Rvalue() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType().createArrayType());
  }

  public void testExprNewArrayGen2Rvalue() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<java.lang.Integer>", null).createArrayType());
  }

  public void testExprNewArrayPrimitive2Lvalue() {
    doTestFirstParamType("meth", "Expr", PsiType.INT);
  }

  public void testExprNewArrayPrimitive2Rvalue() {
    doTestFieldType("myField", "Expr",
                    PsiType.INT.createArrayType().createArrayType());
  }

  public void testExprNewArrayReftype2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceParent", null));
  }

  public void testExprNewArrayReftype2Rvalue() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null).createArrayType().createArrayType());
  }

  public void testExprNewGen() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testExprNewGenExtends() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testExprNewGenSuper() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testExprNewReference() {
    doTestFieldType("myField", "Expr",
                    myJavaFacade.getElementFactory().createTypeFromText("Expr.Subject", null));
  }

  public void testExprReturn2Lvalue() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprReturn2Rvalue() {
    doTestMethodType("meth", "Expr",
                     myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testExprTernary() {
    doTestFirstParamType("meth", "Expr",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.String", null));
  }

  public void testOverridingDown() {
    doTestMethodType("getInt", "Parent", PsiType.BYTE);
  }

  public void testOverridingUp() {
    doTestMethodType("getInt", "Child", PsiType.BYTE);
  }

  public void testSpecJavadoc() {
    JavaCodeStyleSettings settings = getCurrentCodeStyleSettings().getCustomSettings(JavaCodeStyleSettings.class);
    settings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
    doTestFirstParamType("meth", "Spec",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testSpecNotUsed() {
    doTestFieldType("myField", "Spec", PsiType.BOOLEAN);
  }

  public void testTypeArrayReftype2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeArrayReftype2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType().createArrayType());
  }

  public void testTypeArrayRoots2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("Holder", null).createArrayType());
  }

  public void testTypeArrayVararg2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         new PsiEllipsisType(myJavaFacade.getElementFactory().createTypeFromText("Subject", null)));
  }

  public void testTypeArrayVararg2RvalueNeg() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Descendant", null).createArrayType());
  }

  public void testTypeArrayVararg2RvaluePos() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("Subject", null).createArrayType());
  }

  public void testTypeAutoboxBoolean2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Boolean", null));
  }

  public void testTypeAutoboxBoolean2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.BOOLEAN);
  }

  public void testTypeAutoboxByte2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Byte", null));
  }

  public void testTypeAutoboxByte2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.BYTE);
  }

  public void testTypeAutoboxChar2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Character", null));
  }

  public void testTypeAutoboxChar2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.CHAR);
  }

  public void testTypeAutoboxDouble2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Double", null));
  }

  public void testTypeAutoboxDouble2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.DOUBLE);
  }

  public void testTypeAutoboxFloat2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Float", null));
  }

  public void testTypeAutoboxFloat2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.FLOAT);
  }

  public void testTypeAutoboxInt2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Integer", null));
  }

  public void testTypeAutoboxInt2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.INT);
  }

  public void testTypeAutoboxLong2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Long", null));
  }

  public void testTypeAutoboxLong2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.LONG);
  }

  public void testTypeAutoboxShort2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.lang.Short", null));
  }

  public void testTypeAutoboxShort2Rvalue() {
    doTestFieldType("myField", "Type",
                    PsiType.SHORT);
  }

  public void testTypeGenAncestor2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenAncestorWildcard2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? extends Subject>", null));
  }

  public void testTypeGenDescendant2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<Subject>", null));
  }

  public void testTypeGenDescendantWildcard2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("java.util.Set<? super Subject>", null));
  }

  public void testTypeGenRaw2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypeGenRaw2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText(CommonClassNames.JAVA_UTIL_SET, null));
  }

  public void testTypePrimsubBoolean2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.BOOLEAN);
  }

  public void testTypePrimsubBoolean2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.BOOLEAN);
  }

  public void testTypePrimsubByte2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.BYTE);
  }

  public void testTypePrimsubChar2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.CHAR);
  }

  public void testTypePrimsubChar2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.CHAR);
  }

  public void testTypePrimsubDouble2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.DOUBLE);
  }

  public void testTypePrimsubFloat2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.FLOAT);
  }

  public void testTypePrimsubFloat2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.FLOAT);
  }

  public void testTypePrimsubInt2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.INT);
  }

  public void testTypePrimsubInt2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.INT);
  }

  public void testTypePrimsubLong2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.LONG);
  }

  public void testTypePrimsubLong2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.LONG);
  }

  public void testTypePrimsubShort2Lvalue() {
    doTestFirstParamType("meth", "Type", PsiType.SHORT);
  }

  public void testTypePrimsubShort2Rvalue() {
    doTestFieldType("myField", "Type", PsiType.SHORT);
  }

  public void testTypeRefClassChild2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassChild", null));
  }

  public void testTypeRefClassParent2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefClassParent2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("ClassParent", null));
  }

  public void testTypeRefFaceChild2Lvalue() {
    doTestFirstParamType("meth", "Type",
                         myJavaFacade.getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceChild2Rvalue() {
    doTestFieldType("myField", "Type",
                    myJavaFacade.getElementFactory().createTypeFromText("FaceChild", null));
  }

  public void testTypeRefFaceParent2Lvalue() {
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
