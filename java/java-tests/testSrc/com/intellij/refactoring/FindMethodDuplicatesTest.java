/*
 * User: anna
 * Date: 28-Feb-2008
 */
package com.intellij.refactoring;

public class FindMethodDuplicatesTest extends FindMethodDuplicatesBaseTest{

  @Override
  protected String getTestFilePath() {
    return "/refactoring/methodDuplicates/" + getTestName(false) + ".java";
  }

  public void testGenmethExtends() throws Exception {
    doTest();
  }

  public void testGenmethExtendsGenericExact() throws Exception {
    doTest();
  }

  public void testGenmethExtendsGenericWildcard() throws Exception {
    doTest();
  }

  public void testGenmethGeneral() throws Exception {
    doTest();
  }

  public void testGenmethSeveral() throws Exception {
    doTest();
  }

  public void testIdentityComplete() throws Exception {
    doTest();
  }

  public void testIdentityComment() throws Exception {
    doTest();
  }

  public void testIdentityName() throws Exception {
    doTest();
  }

  public void testIdentityWhitespace() throws Exception {
    doTest();
  }

  public void testLocationQuantity() throws Exception {
    doTest();
  }

  public void testSkipNonRelatedCalls() throws Exception {
    doTest(false);
  }

  public void testMappingAny2ParameterPrimitiveLvalue() throws Exception {
    doTest(false);
  }

  public void testMappingExpression2Field() throws Exception {
    doTest(false);
  }

  public void testMappingExpression2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingExpression2ParameterLiterals() throws Exception {
    doTest();
  }

  public void testMappingExpression2ParameterLValues() throws Exception {
    doTest();
  }

  public void testMappingExpression2ParameterMultiple() throws Exception {
    doTest();
  }

  public void testMappingExpression2This() throws Exception {
    doTest(false);
  }

  public void testMappingField2Field() throws Exception {
    doTest();
  }

  public void testMappingField2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingField2Parameter() throws Exception {
    doTest();
  }

  public void testMappingField2This() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2Expression() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2Field() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2LocalVar() throws Exception {
    doTest();
  }

  public void testMappingLocalVar2Parameter() throws Exception {
    doTest();
  }

  public void testMappingLocalVar2This() throws Exception {
    doTest(false);
  }

  public void testMappingMember2MemberDifferent() throws Exception {
    doTest();
  }

  public void testMappingParameter2Field() throws Exception {
    doTest(false);
  }

  public void testMappingParameter2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingParameter2Parameter() throws Exception {
    doTest();
  }

  public void testMappingParameter2This() throws Exception {
    doTest(false);
  }

  public void testMappingThis2Field() throws Exception {
    doTest(false);
  }

  public void testMappingThis2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingThis2Parameter() throws Exception {
    doTest();
  }

  public void testMappingThis2ThisDifferent() throws Exception {
    doTest();
  }

  public void testMappingThis2ThisQualified() throws Exception {
    doTest();
  }

  public void testPostFragmentUsage() throws Exception {
    doTest();
  }

  public void testReturnExpression() throws Exception {
    doTest();
  }

  public void testReturnField() throws Exception {
    doTest();
  }

  public void testReturnLocalVar() throws Exception {
    doTest();
  }

  public void testReturnParameter() throws Exception {
    doTest();
  }

  public void testReturnThis() throws Exception {
    doTest();
  }

  public void testTypesExtends() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturn() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturnDifferentArray() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturnDifferentGeneric() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturnDifferentPrimitive() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturnDifferentReference() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2Concrete() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2ConcreteDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Extends() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2ExtendsDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Super() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2SuperDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Raw() throws Exception {
    doTest();
  }

  public void testTypesGenericsRaw2Concrete() throws Exception {
    doTest();
  }

  public void testTypesGenericsRaw2Raw() throws Exception {
    doTest();
  }

  public void testTypesImplements() throws Exception {
    doTest();
  }

  public void testTypesNoRelationship() throws Exception {
    doTest(false);
  }

  public void testAnonymousTest() throws Exception {
    doTest();
  }

  public void testAnonymousTest1() throws Exception {
    doTest();
  }

  public void testAnonymousTest2() throws Exception {
    doTest(false);
  }

  public void testReturnVoidTest() throws Exception {
    doTest();
  }

  public void testThisReferenceTest() throws Exception {
    doTest();
  }

  public void testAddStaticTest() throws Exception {
    doTest();
  }

  public void testStaticMethodReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement1() throws Exception {
    doTest();
  }

  public void testReturnVariable() throws Exception {
    doTest();
  }

  public void testReturnExpressionDifferent() throws Exception {
    doTest(false);
  }

  public void testTypeInheritance() throws Exception {
    doTest();
  }

  public void testTypeInheritance1() throws Exception {
    doTest(false);
  }

  public void testUnusedParameter() throws Exception {
    doTest();
  }

  public void testUnusedParameter1() throws Exception {
    doTest();
  }

  public void testInheritance() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testDeclarationUsage() throws Exception {
    doTest(false);
  }

  public void testChangingReturnType() throws Exception {
    doTest();
  }
}