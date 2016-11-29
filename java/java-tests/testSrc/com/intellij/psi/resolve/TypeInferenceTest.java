package com.intellij.psi.resolve;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class TypeInferenceTest extends Resolve15TestCase {
  public void testInferInCall1 () throws Exception {
    doTestObject();
  }

  private void doTestObject() throws Exception {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertTrue(type instanceof PsiClassType);
    PsiType[] paramTypes = ((PsiClassType)type).getParameters();
    assertEquals(1, paramTypes.length);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, paramTypes[0].getCanonicalText());
  }

  public void testInferInAssign1 () throws Exception {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertTrue(type instanceof PsiClassType);
    PsiType[] paramTypes = ((PsiClassType)type).getParameters();
    assertEquals(1, paramTypes.length);
    assertEquals( "java.lang.String", paramTypes[0].getCanonicalText());
  }

  public void testInferInAssign2() throws Exception {
    doTestObject();
  }

  public void testInferInCast () throws Exception {
    doTestObject();
  }

  public void testInferWithBounds () throws Exception {
    checkResolvesTo("C.Inner");
  }

  public void testInferWithBounds1 () throws Exception {
    PsiReferenceExpression ref = configure();
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiMethod method = (PsiMethod)resolveResult.getElement();
    PsiType type = substitutor.substitute(method.getTypeParameters()[0]);
    assertEquals("java.lang.String", type.getCanonicalText());
  }

  private PsiReferenceExpression configure() throws Exception {
    return (PsiReferenceExpression)configureByFile("inference/" + getTestName(false) + ".java");
  }

  public void testInferInParamsOnly () throws Exception {
    checkResolvesTo("C.I");
  }

  public void testInferRawType () throws Exception {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  private void checkResolvesTo(@NonNls String typeName) throws Exception {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals(typeName, type.getCanonicalText());
  }

  public void testInferInSuperAssignment () throws Exception {
    checkResolvesTo("B<java.lang.String>");
  }

  public void testInferWithWildcards () throws Exception {
    checkResolvesTo("Collections.SelfComparable");
  }

  public void testInferWithWildcards1 () throws Exception {
    checkResolvesTo("java.lang.String");
  }

  public void testInferWithWildcards2 () throws Exception {
    checkResolvesTo("Collection<BarImpl>");
  }

  public void testInferWithWildcards3 () throws Exception {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferWithWildcards4 () throws Exception {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferWithWildcards5 () throws Exception {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferInReturn () throws Exception {
    checkResolvesTo("T");
  }

  public void testInferAutoboxed () throws Exception {
    checkResolvesTo("java.lang.Integer");
  }

  public void testInferWithVarargs1 () throws Exception {
    checkResolvesTo("C2");
  }

  public void testInferWithVarargs2 () throws Exception {
    checkResolvesTo("C1");
  }

  public void testInferWithVarargs3 () throws Exception {
    checkResolvesTo("List<int[]>");
  }

  public void testInferWithVarargs4 () throws Exception {
    checkResolvesTo("List<int[]>");
  }

  public void testInferWithVarargs5 () throws Exception {
    checkResolvesTo("List<java.lang.Integer>");
  }

  public void testInferWithVarargs6 () throws Exception {
    checkResolvesTo("List<java.lang.Integer[]>");
  }

  public void testInferPrimitiveArray () throws Exception {
    checkResolvesTo("double[]");
  }

  public void testSCR41031 () throws Exception {
    checkResolvesTo("List<T>");
  }

  public void testInferUnchecked () throws Exception {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  public void testInferNotNull () throws Exception {
    checkResolvesTo("E");
  }

  public void testBoundComposition() throws Exception {
    checkResolvesTo("java.lang.Class<? super ? extends java.lang.Object>");
  }
}
