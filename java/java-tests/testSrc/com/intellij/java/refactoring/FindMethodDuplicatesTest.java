/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

public class FindMethodDuplicatesTest extends FindMethodDuplicatesBaseTest{

  @Override
  protected String getTestFilePath() {
    return "/refactoring/methodDuplicates/" + getTestName(false) + ".java";
  }

  public void testGenmethExtends() {
    doTest();
  }

  public void testGenmethExtendsGenericExact() {
    doTest();
  }

  public void testGenmethExtendsGenericWildcard() {
    doTest();
  }

  public void testGenmethGeneral() {
    doTest();
  }

  public void testGenmethSeveral() {
    doTest();
  }

  public void testIdentityComplete() {
    doTest();
  }

  public void testIdentityComment() {
    doTest();
  }

  public void testIdentityName() {
    doTest();
  }

  public void testIdentityWhitespace() {
    doTest();
  }

  public void testLocationQuantity() {
    doTest();
  }

  public void testSkipNonRelatedCalls() {
    doTest(false);
  }

  public void testMappingAny2ParameterPrimitiveLvalue() {
    doTest(false);
  }

  public void testMappingExpression2Field() {
    doTest(false);
  }

  public void testMappingExpression2LocalVar() {
    doTest(false);
  }

  public void testMappingExpression2ParameterLiterals() {
    doTest();
  }

  public void testMappingExpression2ParameterLValues() {
    doTest();
  }

  public void testMappingExpression2ParameterMultiple() {
    doTest();
  }

  public void testMappingExpression2This() {
    doTest(false);
  }

  public void testMappingField2Field() {
    doTest();
  }

  public void testMappingField2LocalVar() {
    doTest(false);
  }

  public void testMappingField2Parameter() {
    doTest();
  }

  public void testMappingField2This() {
    doTest(false);
  }

  public void testMappingLocalVar2Expression() {
    doTest(false);
  }

  public void testMappingLocalVar2Field() {
    doTest(false);
  }

  public void testMappingLocalVar2LocalVar() {
    doTest();
  }

  public void testMappingLocalVar2Parameter() {
    doTest();
  }

  public void testMappingLocalVar2This() {
    doTest(false);
  }

  public void testMappingMember2MemberDifferent() {
    doTest();
  }

  public void testMappingParameter2Field() {
    doTest(false);
  }

  public void testMappingParameter2LocalVar() {
    doTest(false);
  }

  public void testMappingParameter2Parameter() {
    doTest();
  }

  public void testMappingParameter2This() {
    doTest(false);
  }

  public void testMappingThis2Field() {
    doTest(false);
  }

  public void testMappingThis2LocalVar() {
    doTest(false);
  }

  public void testMappingThis2Parameter() {
    doTest();
  }

  public void testMappingThis2ThisDifferent() {
    doTest();
  }

  public void testMappingThis2ThisQualified() {
    doTest();
  }

  public void testPostFragmentUsage() {
    doTest();
  }

  public void testReturnExpression() {
    doTest();
  }

  public void testReturnField() {
    doTest();
  }

  public void testReturnLocalVar() {
    doTest();
  }

  public void testReturnParameter() {
    doTest();
  }

  public void testReturnThis() {
    doTest();
  }

  public void testTypesExtends() {
    doTest();
  }

  public void testTypesExtendsReturn() {
    doTest();
  }

  public void testTypesExtendsReturnDifferentArray() {
    doTest();
  }

  public void testTypesExtendsReturnDifferentGeneric() {
    doTest();
  }

  public void testTypesExtendsReturnDifferentPrimitive() {
    doTest();
  }

  public void testTypesExtendsReturnDifferentReference() {
    doTest();
  }

  public void testTypesGenericsConcrete2Concrete() {
    doTest();
  }

  public void testTypesGenericsConcrete2ConcreteDifferent() {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Extends() {
    doTest();
  }

  public void testTypesGenericsConcrete2ExtendsDifferent() {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Super() {
    doTest();
  }

  public void testTypesGenericsConcrete2SuperDifferent() {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Raw() {
    doTest();
  }

  public void testTypesGenericsRaw2Concrete() {
    doTest();
  }

  public void testTypesGenericsRaw2Raw() {
    doTest();
  }

  public void testTypesImplements() {
    doTest();
  }

  public void testTypesNoRelationship() {
    doTest(false);
  }

  public void testAnonymousTest() {
    doTest();
  }

  public void testAnonymousTest1() {
    doTest();
  }

  public void testAnonymousTest2() {
    doTest(false);
  }

  public void testReturnVoidTest() {
    doTest();
  }

  public void testThisReferenceTest() {
    doTest();
  }

  public void testAddStaticTest() {
    doTest();
  }

  public void testStaticMethodReplacement() {
    doTest();
  }

  public void testRefReplacement() {
    doTest();
  }

  public void testRefReplacement1() {
    doTest();
  }

  public void testReturnVariable() {
    doTest();
  }

  public void testReturnExpressionDifferent() {
    doTest(false);
  }

  public void testTypeInheritance() {
    doTest();
  }

  public void testTypeInheritance1() {
    doTest(false);
  }

  public void testUnusedParameter() {
    doTest();
  }

  public void testUnusedParameter1() {
    doTest();
  }

  public void testInheritance() {
    doTest();
  }

  public void testVarargs() {
    doTest();
  }

  public void testDeclarationUsage() {
    doTest(false);
  }

  public void testChangingReturnType() {
    doTest();
  }
}