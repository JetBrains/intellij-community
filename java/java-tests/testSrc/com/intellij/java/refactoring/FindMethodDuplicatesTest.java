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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.refactoring.FindMethodDuplicatesFixtureKt.findMethodDuplicatesFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class FindMethodDuplicatesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_7));
  private final TestFixture<FindMethodDuplicatesFixture> duplicates = findMethodDuplicatesFixture(projectFixture, "/refactoring/methodDuplicates/");

  @Test
  public void testGenmethExtends() {
    duplicates.get().doTest();
  }

  @Test
  public void testGenmethExtendsGenericExact() {
    duplicates.get().doTest();
  }

  @Test
  public void testGenmethExtendsGenericWildcard() {
    duplicates.get().doTest();
  }

  @Test
  public void testGenmethGeneral() {
    duplicates.get().doTest();
  }

  @Test
  public void testGenmethSeveral() {
    duplicates.get().doTest();
  }

  @Test
  public void testIdentityComplete() {
    duplicates.get().doTest();
  }

  @Test
  public void testIdentityComment() {
    duplicates.get().doTest();
  }

  @Test
  public void testIdentityName() {
    duplicates.get().doTest();
  }

  @Test
  public void testIdentityWhitespace() {
    duplicates.get().doTest();
  }

  @Test
  public void testLocationQuantity() {
    duplicates.get().doTest();
  }

  @Test
  public void testSkipNonRelatedCalls() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingAny2ParameterPrimitiveLvalue() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingExpression2Field() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingExpression2LocalVar() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingExpression2ParameterLiterals() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingExpression2ParameterLValues() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingExpression2ParameterMultiple() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingExpression2This() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingField2Field() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingField2LocalVar() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingField2Parameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingField2This() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingLocalVar2Expression() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingLocalVar2Field() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingLocalVar2LocalVar() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingLocalVar2Parameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingLocalVar2This() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingMember2MemberDifferent() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingParameter2Field() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingParameter2LocalVar() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingParameter2Parameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingParameter2This() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingThis2Field() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingThis2LocalVar() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testMappingThis2Parameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingThis2ThisDifferent() {
    duplicates.get().doTest();
  }

  @Test
  public void testMappingThis2ThisQualified() {
    duplicates.get().doTest();
  }

  @Test
  public void testPostFragmentUsage() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnExpression() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnField() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnLocalVar() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnParameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnThis() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtends() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtendsReturn() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtendsReturnDifferentArray() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtendsReturnDifferentGeneric() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtendsReturnDifferentPrimitive() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesExtendsReturnDifferentReference() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsConcrete2Concrete() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsConcrete2ConcreteDifferent() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testTypesGenericsConcrete2Extends() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsConcrete2ExtendsDifferent() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testTypesGenericsConcrete2Super() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsConcrete2SuperDifferent() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testTypesGenericsConcrete2Raw() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsRaw2Concrete() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesGenericsRaw2Raw() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesImplements() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypesNoRelationship() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testAnonymousTest() {
    duplicates.get().doTest();
  }

  @Test
  public void testAnonymousTest1() {
    duplicates.get().doTest();
  }

  @Test
  public void testAnonymousTest2() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testReturnVoidTest() {
    duplicates.get().doTest();
  }

  @Test
  public void testThisReferenceTest() {
    duplicates.get().doTest();
  }

  @Test
  public void testAddStaticTest() {
    duplicates.get().doTest();
  }

  @Test
  public void testStaticMethodReplacement() {
    duplicates.get().doTest();
  }

  @Test
  public void testRefReplacement() {
    duplicates.get().doTest();
  }

  @Test
  public void testRefReplacement1() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnVariable() {
    duplicates.get().doTest();
  }

  @Test
  public void testReturnExpressionDifferent() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testTypeInheritance() {
    duplicates.get().doTest();
  }

  @Test
  public void testTypeInheritance1() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testUnusedParameter() {
    duplicates.get().doTest();
  }

  @Test
  public void testUnusedParameter1() {
    duplicates.get().doTest();
  }

  @Test
  public void testInheritance() {
    duplicates.get().doTest();
  }

  @Test
  public void testVarargs() {
    duplicates.get().doTest();
  }

  @Test
  public void testDeclarationUsage() {
    duplicates.get().doTest(false);
  }

  @Test
  public void testChangingReturnType() {
    duplicates.get().doTest();
  }
}