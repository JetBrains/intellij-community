// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.idea.TestFor;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

//javac option to dump bounds: -XDdumpInferenceGraphsTo=
@TestApplication
public class GenericsHighlighting8Test {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new UncheckedWarningLocalInspection()));

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting8";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    myFixture.enableInspections(new UnusedDeclarationInspection());
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.enableInspections(new JavacQuirksInspection());
  }

  @Test
  public void testReferenceTypeParams() {
    doTest();
  }
  @Test
  public void testTypeParameterBoundsList() {
    doTest();
  }
  @Test
  public void testClassInheritance() {
    doTest();
  }
  @Test
  public void testTypeInference() {
    doTest();
  }
  @Test
  public void testRaw() {
    doTest(true);
  }
  @Test
  public void testExceptions() {
    doTest();
  }
  @Test
  public void testExplicitMethodParameters() {
    doTest();
  }
  @Test
  public void testInferenceWithBounds() {
    doTest();
  }
  @Test
  public void testInferenceWithSuperBounds() {
    doTest();
  }
  @Test
  public void testInferenceWithUpperBoundPromotion() {
    doTest();
  }
  @Test
  public void testVariance() {
    doTest();
  }
  @Test
  public void testForeachTypes() {
    doTest();
  }
  @Test
  public void testRawOverridingMethods() {
    doTest();
  }    
  @Test
  public void testAutoboxing() {
    doTest();
  }                
  @Test
  public void testAutoboxingMethods() {
    doTest();
  }                                             
  @Test
  public void testAutoboxingConstructors() {
    doTest();
  }                                      
  @Test
  public void testEnumWithAbstractMethods() {
    doTest();
  }                                   
  @Test public void testEnum() { doTest(); }
  @Test
  public void testEnum56239() {
    doTest();
  }
  @Test
  public void testSameErasure() {
    doTest();
  }          
  @Test
  public void testMethods() {
    doTest();
  }            
  @Test
  public void testFields() {
    doTest();
  }           
  @Test
  public void testStaticImports() {
    doTest(true);
  }   
  @Test
  public void testUncheckedCasts() {
    doTest(true);
  }
  @Test
  public void testUncheckedOverriding() {
    doTest(true);
  }
  @Test
  public void testWildcardTypes() {
    doTest(true);
  }
  @Test
  public void testConvertibleTypes() {
    doTest(true);
  }
  @Test
  public void testIntersectionTypes() {
    doTest(true);
  }
  @Test
  public void testVarargs() {
    doTest(true);
  }
  @Test
  public void testTypeArgsOnRaw() {
    doTest();
  }          
  @Test
  public void testConditionalExpression() {
    doTest();
  }  
  @Test
  public void testUnused() {
    doTest(true);
  }            
  @Test
  public void testIDEADEV7337() {
    doTest(true);
  }      
  @Test
  public void testIDEADEV10459() {
    doTest(true);
  }     
  @Test
  public void testIDEADEV12951() {
    doTest(true);
  }     
  @Test
  public void testIDEADEV13011() {
    doTest(true);
  }     
  @Test
  public void testIDEADEV14006() {
    doTest(true);
  }     
  @Test
  public void testIDEADEV14103() {
    doTest(true);
  }        
  @Test
  public void testIDEADEV15534() {
    doTest(true);
  }      
  @Test
  public void testIDEADEV23157() {
    doTest(true);
  }    
  @Test
  public void testIDEADEV24166() {
    doTest(true);
  }  
  @Test
  public void testIDEADEV57343() {
    doTest();
  }
  @Test
  public void testSOE() {
    doTest(true);
  }        
  @Test
  public void testGenericExtendException() {
    doTest();
  }  
  @Test
  public void testSameErasureDifferentReturnTypes() {
    doTest();
  }
  @Test
  public void testDeepConflictingReturnTypes() {
    doTest();
  }  
  @Test
  public void testInheritFromTypeParameter() {
    doTest();
  }     
  @Test
  public void testAnnotationsAsPartOfModifierList() {
    doTest();
  } 
  @Test
  public void testImplementAnnotation() {
    doTest();
  }      
  @Test
  public void testOverrideAtLanguageLevel6() {
    doTest();
  }    
  @Test
  public void testSuperMethodCallWithErasure() {
    doTest();
  } 
  @Test
  public void testWildcardCastConversion() {
    doTest();
  }    
  @Test
  public void testTypeWithinItsWildcardBound() {
    doTest();
  } 
  @Test
  public void testMethodSignatureEquality() {
    doTest();
  }
  @Test
  public void testTypeParameterBoundsOrder() {
    doTest();
  }
  @Test
  public void testInnerClassRef() {
    doTest();
  }             
  @Test
  public void testPrivateInnerClassRef() {
    doTest();
  }      
  @Test
  public void testWideningCastToTypeParam() {
    doTest();
  }     
  @Test
  public void testCapturedWildcardAssignments() {
    doTest();
  }        
  @Test
  public void testTypeParameterBoundVisibility() {
    doTest();
  }
  @Test
  public void testUncheckedWarningsLevel6() {
    doTest(true);
  }
  @Test
  public void testIDEA77991() {
    doTest();
  }                
  @Test
  public void testIDEA80386() {
    doTest();
  }               
  @Test
  public void testIDEA66311() {
    doTest();
  }  
  @Test
  public void testIDEA67672() {
    doTest();
  }  
  @Test
  public void testIDEA88895() {
    doTest();
  }  
  @Test
  public void testIDEA67667() {
    doTest();
  }  
  @Test
  public void testIDEA66311_16() {
    doTest();
  }
  @Test
  public void testIDEA76283() {
    doTest();
  }
  @Test
  public void testIDEA74899() {
    doTest();
  }  
  @Test
  public void testIDEA63291() {
    doTest();
  }  
  @Test
  public void testIDEA72912() {
    doTest();
  }         
  @Test
  public void testIllegalGenericTypeInInstanceof() {
    doTest();
  } 
  @Test
  public void testIDEA57339() {
    doTest();
  } 
  @Test
  public void testIDEA57340() {
    doTest();
  }
  @Test
  public void testIDEA89771() {
    doTest();
  }
  @Test
  public void testIDEA89801() {
    doTest();
  }
  @Test
  public void testIDEA67681() {
    doTest();
  }
  @Test
  public void testIDEA67599() {
    doTest();
  }
  @Test
  public void testIDEA57668() {
    doTest();
  }
  @Test
  public void testIDEA57667() {
    doTest();
  }
  @Test
  public void testIDEA57650() {
    doTest();
  }
  @Test
  public void testIDEA57378() {
    doTest();
  }
  @Test
  public void testIDEA57557() {
    doTest();
  }
  @Test
  public void testIDEA57563() {
    doTest();
  }
  @Test
  public void testIDEA57275() {
    doTest();
  }
  @Test
  public void testIDEA57533() {
    doTest();
  }
  @Test
  public void testIDEA57509() {
    doTest();
  }
  @Test
  public void testIDEA57410() {
    doTest();
  }
  @Test
  public void testIDEA57411() {
    doTest();
  }
  @Test
  public void testIDEA57484() {
    doTest();
  }
  @Test
  public void testIDEA57485() {
    doTest();
  }
  @Test
  public void testIDEA57486() {
    doTest();
  }

  //compiles with java 6
  public void _testIDEA57492() {
    doTest();
  }
  
  //compiles with java 6
  public void _testIDEA57493() {
    doTest();
  }
  @Test
  public void testIDEA57495() {
    doTest();
  }
  @Test
  public void testIDEA57494() {
    doTest();
  }
  @Test
  public void testIDEA57496() {
    doTest();
  }
  @Test
  public void testIDEA57264() {
    doTest();
  }
  @Test
  public void testIDEA57315() {
    doTest();
  }
  @Test
  public void testIDEA57346() {
    doTest();
  }
  @Test
  public void testIDEA57284() {
    doTest();
  }
  @Test
  public void testIDEA57286() {
    doTest();
  }
  @Test
  public void testIDEA57307() {
    doTest(true);
  }
  @Test
  public void testIDEA57308() {
    doTest();
  }
  @Test
  public void testIDEA57310() {
    doTest();
  }
  @Test
  public void testIDEA57311() {
    doTest();
  }
  @Test
  public void testIDEA57309() {
    doTest();
  }
  @Test
  public void testIDEA90802() {
    doTest();
  }
  @Test
  public void testIDEA70370() {
    doTest(true);
  }
  @Test
  public void testInaccessibleThroughWildcard() {
    doTest();
  }
  @Test
  public void testInconvertibleTypes() {
    doTest();
  }
  @Test
  public void testIncompatibleReturnType() {
    doTest();
  }
  @Test
  public void testContinueInferenceAfterFirstRawResult() {
    doTest();
  }
  @Test
  public void testDoNotAcceptLowerBoundIfRaw() {
    doTest();
  }
  @Test
  public void testStaticOverride() {
    doTest();
  }
  @Test
  public void testTypeArgumentsGivenOnRawType() {
    doTest();
  }
  @Test
  public void testSelectFromTypeParameter() {
    doTest();
  }
  @Test
  public void testTypeArgumentsGivenOnAnonymousClassCreation() {
    doTest();
  }

  @Test
  public void testIDEA94011() {
    doTest();
  }
  @Test
  public void testDifferentTypeParamsInOverloadedMethods() {
    doTest(true);
  }

  @Test
  public void testIDEA91626() {
    doTest(true);
  }
  @Test
  public void testIDEA92022() {
    doTest();
  }
  @Test
  public void testRawOnParameterized() {
    doTest();
  }
  @Test
  public void testFailedInferenceWithBoxing() {
    doTest();
  }
  @Test
  public void testFixedFailedInferenceWithBoxing() {
    doTest();
  }
  @Test
  public void testInferenceWithBoxingCovariant() {
    doTest();
  }
  @Test
  public void testSuperWildcardIsNotWithinItsBound() {
    doTest();
  }
  @Test
  public void testSpecificReturnType() {
    doTest();
  }
  @Test
  public void testParameterizedParameterBound() {
    doTest();
  }
  @Test
  public void testInstanceClassInStaticContextAccess() {
    doTest();
  }
  @Test
  public void testFlattenIntersectionType() {
    doTest();
  }
  @Test
  public void testIDEA97276() {
    doTest();
  }
  @Test
  public void testWildcardsBoundsIntersection() {
    doTest();
  }
  @Test
  public void testOverrideWithMoreSpecificReturn() {
    doTest();
  }
  @Test
  public void testIDEA97888() {
    doTest();
  }
  @Test
  public void testMethodCallParamsOnRawType() {
    doTest();
  }
  @Test
  public void testIDEA98421() {
    doTest();
  }
  @Test
  public void testErasureTypeParameterBound() {
    doTest();
  }
  @Test
  public void testThisAsAccessObject() {
    doTest();
  }
  @Test
  public void testIDEA67861() {
    doTest();
  }
  @Test
  public void testIDEA67597() {
    doTest();
  }
  @Test
  public void testIDEA57539() {
    doTest();
  }
  @Test
  public void testIDEA67570() {
    doTest();
  }
  @Test
  public void testIDEA99061() {
    doTest();
  }
  @Test
  public void testIDEA99347() {
    doTest();
  }
  @Test
  public void testIDEA86875() {
    doTest();
  }
  @Test
  public void testIDEA103760(){
    doTest();
  }
  @Test
  public void testIDEA105846(){
    doTest();
  }
  @Test
  public void testIDEA105695(){
    doTest();
  }
  @Test
  public void testIDEA104992(){
    doTest();
  }
  @Test
  public void testIDEA57446(){
    doTest();
  }
  @Test
  public void testIDEA67677(){
    doTest();
  }
  @Test
  public void testIDEA67798(){
    doTest();
  }
  @Test
  public void testIDEA57534(){
    doTest();
  }
  @Test
  public void testIDEA57482(){
    doTest();
  }
  @Test
  public void testIDEA67577(){
    doTest();
  }
  @Test
  public void testIDEA57413(){
    doTest();
  }
  @Test
  public void testIDEA57265(){
    doTest();
  }
  @Test
  public void testIDEA57271(){
    doTest();
  }
  @Test
  public void testIDEA57272(){
    doTest();
  }
  @Test
  public void testIDEA57285(){
    doTest();
  }
  @Test
  public void testIDEA65066(){
    doTest();
  }
  @Test
  public void testIDEA67998(){
    doTest();
  }
  @Test
  public void testIDEA18425(){
    doTest();
  }
  @Test
  public void testIDEA27080(){
    doTest();
  }
  @Test
  public void testIDEA22079(){
    doTest();
  }
  @Test
  public void testIDEA21602(){
    doTest();
  }
  @Test
  public void testIDEA21602_7(){
    doTest();
  }

  @Test
  public void testIDEA21597() {
    doTest();
  }
  @Test
  public void testIDEA20573() {
    doTest();
  }
  @Test
  public void testIDEA20244() {
    doTest();
  }
  @Test
  public void testIDEA22005() {
    doTest();
  }
  @Test
  public void testIDEA57259() {
    doTest();
  }
  @Test
  public void testIDEA107957() {
    doTest();
  }
  @Test
  public void testIDEA109875() {
    doTest();
  }
  @Test
  public void testIDEA106964() {
    doTest();
  }
  @Test
  public void testIDEA107782() {
    doTest();
  }
  @Test
  public void testInheritedWithDifferentArgsInTypeParams() {
    doTest();
  }
  @Test
  public void testIllegalForwardReferenceInTypeParameterDefinition() {
    doTest();
  }

  @Test
  public void testIDEA57877() {
    doTest();
  }
  @Test
  public void testIDEA110568() {
    doTest();
  }
  @Test
  public void testSelfRef() {
    doTest();
  }
  @Test
  public void testTypeParamsCyclicInference() {
    doTest();
  }
  @Test
  public void testCaptureTopLevelWildcardsForConditionalExpression() {
    doTest();
  }
  @Test
  public void testGenericsOverrideMethodInRawInheritor() {
    doTest();
  }

  @Test
  public void testIDEA107654() {
    doTest();
  }

  @Test
  public void testIDEA55510() {
    doTest(true);
  }

  @Test
  public void testIDEA27185(){
    doTest();
  }
  @Test
  public void testIDEA67571(){
    doTest();
  }
  @Test
  public void testTypeArgumentsOnRawType(){
    doTest();
  }

  @Test
  public void testTypeArgumentsOnRawType17(){
    doTest();
  }

  @Test
  public void testWildcardsOnRawTypes() {
    doTest();
  }
  @Test
  public void testDisableWithinBoundsCheckForSuperWildcards() {
    doTest();
  }

  @Test
  public void testIDEA108287() {
    doTest(true);
  }

  @Test
  public void testIDEA77128() {
    doTest();
  }

  @Test
  public void testDisableCastingToNestedWildcards() {
    doTest();
  }

  @Test
  public void testBooleanInferenceFromIfCondition() {
    doTest();
  }

  @Test
  public void testMethodCallOnRawTypesExtended() {
    doTest();
  }

  @Test
  public void testIDEA104100() {
    doTest();
  }
  @Test
  public void testIDEA104160() {
    doTest();
  }
  @Test
  public void testSOEInLeastUpperClass() {
    doTest();
  }

  @Test
  public void testIDEA57334() {
    doTest();
  }

  @Test
  public void testIDEA57325() {
    doTest();
  }
  @Test
  public void testIDEA67835() {
    doTest();
  }
  @Test
  public void testIDEA67744() {
    doTest();
  }
  @Test
  public void testIDEA67682() {
    doTest();
  }
  @Test
  public void testIDEA57391() {
    doTest();
  }
  @Test
  public void testIDEA110869() {
    doTest();
  }
  @Test public void testIDEA110947() { doTest(false); }
  @Test
  public void testIDEA112122() {
    doTest();
  }
  @Test
  public void testNoInferenceFromTypeCast() {
    doTest();
  }
  @Test
  public void testCaptureWildcardsInTypeCasts() {
    doTest();
  }
  @Test
  public void testIDEA111085() {
    doTest();
  }
  @Test
  public void testIDEA109556() {
    doTest();
  }
  @Test
  public void testIDEA107440() {
    doTest();
  }
  @Test
  public void testIDEA57289() {
    doTest();
  }
  @Test
  public void testIDEA57439() {
    doTest();
  }
  @Test
  public void testIDEA57312() {
    doTest();
  }
  @Test
  public void testIDEA67865() {
    doTest();
  }
  @Test
  public void testBoxingSpecific() {
    doTest();
  }
  @Test
  public void testIDEA67843() {    //fixme need to change test
    doTest();
  }
  @Test
  public void testAmbiguousTypeParamVsConcrete() {
    doTest();
  }
  @Test
  public void testRawAssignments() {
    doTest();
  }
  @Test
  public void testIDEA87860() {
    doTest();
  }

  @Test
  public void testIDEA114797() {
    doTest();
  }

  @Test
  public void testCastToIntersectionType() {
    doTest();
  }

  @Test
  public void testCastToIntersection() {
    doTest();
  }

  @Test
  public void testIDEA122401() {
    doTest();
  }

  @Test
  public void testCaptureInsideNestedCalls() {
    doTest();
  }

  @Test public void testSuperWildcardWithBoundPromotion() { doTest();}

  @Test public void testErasure() { doTest(); }

  @Test
  public void testWildcardBoundsCombination() {
    doTest();
  }

  @Test
  public void testIDEA128333() {
    doTest();
  }

  @Test public void testIDEA78402() { doTest(); }

  @Test
  public void testUncheckedWarningInsideLambdaReturnStatement() {
    doTest(true);
  }

  @Test
  public void testInferredParameterInBoundsInRecursiveGenerics() {
    doTest(false);
  }

  @Test
  public void testSuperWildcardCapturedSuperExtendsWildcardCapturedExtends() {
    doTest(false);
  }

  @Test
  public void testRejectContradictingEqualsBounds() {
    doTest(false);
  }

  @Test
  public void testRejectEqualsBoundsContradictingLowerBound() {
    doTest(false);
  }

  @Test
  public void testSuperInterfaceMethodCalledByMatterOfInterface() {
    doTest(false);
  }

  private void doTest() {
    doTest(false);
  }

   private void doTest(boolean warnings) {
     LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
     daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_8);
     IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
     daemonFixture.get().doTest(BASE_PATH + "/" + myFixture.getTestName(false) + ".java", warnings, false);
   }


  @Test
  public void testIDEA67584() {
    doTest();
  }
  @Test
  public void testIDEA113225() {
    doTest();
  }

  @Test
  public void testIDEA139069() {
    doTest();
  }

  @Test
  public void testIDEA67745() {
    doTest();
  }

  @Test
  public void testIDEA57313() {
    doTest();
  }

  @Test
  public void testIDEA57387() {
    doTest();
  }

  @Test
  public void testIDEA57314() {
    doTest();
  }

  @Test
  public void testIDEA57322() {
    doTest();
  }

  @Test
  public void testIDEA57362() {
    doTest();
  }

  @Test
  public void testIDEA57320() {
    doTest();
  }

  @Test
  public void testIDEA139090() {
    doTest();
  }

  @Test
  public void testIDEA57502() {
    doTest();
  }

  @Test
  public void testIDEA67746() {
    doTest();
  }

  @Test
  public void testIDEA67592() {
    doTest();
  }

  @Test
  public void testIDEA93713() {
    doTest();
  }

  @Test
  public void testIDEA107713() {
    doTest();
  }

  @Test
  public void testExceptionCollectionWithLambda() {
    doTest();
  }

  @Test
  public void testUncheckedWarningsWhenInferredTypeLeadsToRawRoGenericAssignment() {
    doTest(true);
  }

  @Test
  public void testExpectedTypeBasedOnArrayCreationWithoutExplicitType() {
    doTest();
  }

  @Test
  public void testIDEA148348() {
    doTest();
  }

  @Test
  public void testIDEA148361() {
    doTest();
  }

  @Test
  public void testIDEA134059() {
    doTest();
  }

  @Test
  public void testIDEA139222() {
    doTest();
  }

  @Test
  public void testIDEA139156() {
    doTest();
  }

  @Test
  public void testIDEA139169() {
    doTest();
  }

  @Test
  public void testIDEA131686() {
    doTest();
  }

  @Test
  public void testIDEA56754() {
    doTest();
  }

  @Test
  public void testAccessClassForWildcardCaptureType() {
    doTest();
  }

  @Test
  public void testDistinguishTypeArgs() {
    doTest();
  }

  @Test
  public void testRecursiveCapturedWildcardTypes() {
    doTest();
  }

  @Test
  public void testRecursiveCapturedWildcardTypesIDEA139167() {
    doTest();
  }
  
  @Test
  public void testRecursiveCapturedWildcardTypesIDEA139157() {
    doTest();
  }

  @Test
  public void testIDEA146897() {
    doTest();
  }

  @Test
  public void testIDEA139096() {
    doTest();
  }

  @Test
  public void testCastingCapturedWildcardToPrimitive() {
    doTest();
  }

  @Test
  public void testCastingCapturedWildcardToArray() {
    doTest();
  }

  @Test
  public void testCheckUncheckedAssignmentDuringVariablesResaolution() {
    doTest(true);
  }

  @Test
  public void testRetrieveInferenceErrorsFromContainingCallsIfCurrentDoesNotProvideAny() {
    doTest();
  }

  @Test
  public void testForeachOverCapturedWildcardWithCollectionUpperBound() {
    doTest();
  }

  @Test
  public void testCapturedWildcardWithPrimitiveTypesChecks() {
    doTest();
  }

  @Test
  public void testCapturedWildcardPackageLocalAccess() {
    doTest();
  }

  @Test
  public void testCapturedWildcardPassedThroughMethodCallChain() {
    doTest();
  }

  @Test
  public void testIDEA152179() {
    doTest();
  }

  @Test
  public void testLooseInvocationContextForProperPrimitiveTypes() {
    doTest();
  }

  @Test
  public void testUncheckedWarningsInsideIncorporationPhase() {
    doTest();
  }

  @Test
  public void testUnifiedSubstitutorUpInTheHierarchy() {
    doTest();
  }

  @Test
  public void testNestedCaptures() {
    doTest();
  }

  @Test
  public void testErasureOfReturnTypeOfNonGenericMethod() {
    doTest();
  }

  @Test
  public void testUncheckedCastWithCapturedWildcards() {
    doTest(true);
  }

  @Test
  public void testReifiableCapturedWildcards() {
    doTest(true);
  }

  @Test
  public void testUncheckedWarningsInsideLambda() {
    doTest(true);
  }

  @Test
  public void testLowerBoundOfCapturedWildcardInSubtypingConstraint() {
    doTest(true);
  }

  @Test
  public void testMembersContainedInCapturedWildcardType() {
    doTest();
  }

  @Test public void testArrayTypeUpperBound() { doTest(); }

  @Test public void testNonGenericInnerOfGenericOuter() { doTest(); }
  @Test public void testIDEA53597() { doTest(); }

  @Test
  public void testTypeParameterBoundsWithSubstitutionWhenMethodHierarchyIsChecked() {
    doTest();
  }

  @Test
  public void testBoundsPromotionForDerivedType() {
    doTest();
  }

  @Test
  public void testSameErasureForStaticMethodsInInterfaces() {
    doTest();
  }

  @Test
  public void testConditionalExpressionInIncompleteCall() {
    doTest();
  }
  
  @Test
  public void testIDEA194093 () {
    doTest();
  }

  @Test
  public void testTooltipTypesAgree() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: " + greyed + "\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: " + toolTipForeground + "\">Generic</span></td>" +
                      "<td style='padding: 0px 0px 8px 0px;'>&lt;<span style=\"color: " + toolTipForeground + "\">? extends Number</span>,</td>" +
                      "<td style='padding: 0px 0px 8px 0px;'><span style=\"color: " + toolTipForeground + "\">Number</span>,</td>" +
                      "<td style='padding: 0px 0px 8px 0px;'><span style=\"color: " + toolTipForeground + "\">Integer</span>&gt;</td></tr>" +
                      "<tr><td style=\"padding: 0px 16px 0px 4px; color: " + greyed + "\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: " + toolTipForeground + "\">Generic</span></td>" +
                      "<td style='padding: 0px 0px 0px 0px;'>&lt;<span style=\"color: " + toolTipForeground + "\">Integer</span>,</td>" +
                      "<td style='padding: 0px 0px 0px 0px;'><span style=\"color: " + red + "\">Integer</span>,</td>" +
                      "<td style='padding: 0px 0px 0px 0px;'><span style=\"color: " + toolTipForeground + "\">Integer</span>&gt;</td>" +
                      "</tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testTooltipNotEnoughArguments() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: " + greyed + "\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: " + toolTipForeground + "\">int</span></td></tr>" +
                      "<tr><td style=\"padding: 0px 16px 0px 4px; color: " + greyed + "\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: " + red + "\">String</span></td></tr>" +
                      "</table>" +
                      "</html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testVarargsTooltip() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: " + greyed + "\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: " + toolTipForeground + "\">String</span></td>" +
                      "</tr>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 0px 4px; color: " + greyed + "\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: " + red + "\">int</span></td>" +
                      "</tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testVarargsTooltip2() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String paramBgColor = ColorUtil.toHtmlColor(EditorColorsUtil.getGlobalOrDefaultColorScheme()
                                                  .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
                                                  .getBackgroundColor());
    String expected = "<html><table>" +
                      "<tr><td/><td style=\"padding-left: 16px; padding-right: 24px; color: " + greyed + "\">Required type:</td>" +
                      "<td style=\"padding-right: 28px; color: " + greyed + "\">Provided:</td></tr>" +
                      "<tr><td><table><tr><td style=\"padding:1px 4px 1px 4px; color: " + greyed + "; background-color: " + paramBgColor + "\">list:</td></tr></table></td>" +
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><span style=\"color: " + toolTipForeground + "\">String...</span></td>" +
                      "<td style=\"padding-right: 28px;\"><span style=\"color: " + red + "\">int</span></td></tr>" +
                      "<tr><td/><td style=\"padding-left: 16px; padding-right: 24px;\"/><td style=\"padding-right: 28px;\"><span style=\"color: " + red + "\">int</span></td></tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testTooltipShortTypeNames() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: "+greyed+"\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: "+toolTipForeground+"\">CharSequence</span></td>" +
                      "</tr>" +
                      "<tr><td style=\"padding: 0px 16px 0px 4px; color: "+greyed+"\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: "+red+"\">int</span></td></tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testTooltipWithCapture() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 8px 4px; color: "+greyed+"\">Required type:</td>" +
                      "<td style=\"padding: 0px 4px 8px 0px;\"><span style=\"color: "+toolTipForeground+"\">Class</span></td>" +
                      "<td style='padding: 0px 0px 8px 0px;'>&lt;<span style=\"color: "+toolTipForeground+"\">capture of ?</span>&gt;</td>" +
                      "</tr>" +
                      "<tr>" +
                      "<td style=\"padding: 0px 16px 0px 4px; color: "+greyed+"\">Provided:</td>" +
                      "<td style=\"padding: 0px 4px 0px 0px;\"><span style=\"color: "+toolTipForeground+"\">Class</span></td>" +
                      "<td style='padding: 0px 0px 0px 0px;'>&lt;<span style=\"color: "+red+"\">capture of ?</span>&gt;</td></tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test
  public void testTooltipComponents() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(NamedColorUtil.getErrorForeground());
    String paramBgColor = ColorUtil.toHtmlColor(EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
      .getBackgroundColor());
    String expected = "<html><table>" +
                      "<tr>" +
                      "<td/>" +
                      "<td style=\"padding-left: 16px; padding-right: 24px; color: " + greyed + "\">Required type:</td>" +
                      "<td style=\"padding-right: 28px; color: " + greyed + "\">Provided:</td></tr>" +
                      "<tr>" +
                      "<td><table><tr><td style=\"padding:1px 4px 1px 4px; color: " + greyed + "; background-color: " + paramBgColor + "\">integerList:</td></tr></table></td>" +
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><span style=\"color: " + toolTipForeground + "\">List&lt;Integer&gt;</span></td>" +
                      "<td style=\"padding-right: 28px;\"><span style=\"color: " + toolTipForeground + "\">List</span>&lt;<span style=\"color: " + red + "\">String</span>&gt;</td></tr>" +
                      "<tr>" +
                      "<td><table><tr><td style=\"padding:1px 4px 1px 4px; color: " + greyed + "; background-color: " + paramBgColor + "\">stringList:</td></tr></table></td>" +
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><span style=\"color: " + toolTipForeground + "\">List&lt;String&gt;</span></td>" +
                      "<td style=\"padding-right: 28px;\"><span style=\"color: " + toolTipForeground + "\">List</span>&lt;<span style=\"color: " + red + "\">Integer</span>&gt;</td></tr>" +
                      "</table></html>";

    daemonFixture.get().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> assertEquals(expected, info.getToolTip()));
  }

  @Test public void testBridgeMethodOverriding() { doTest(); }
  @Test public void testClassLiteralType() { doTest(); }
  @Test public void testNestedWildcardsWithImplicitBounds() { doTest(); }
  @Test public void testCallOnRawWithExplicitTypeArguments() { doTest(); }
  @Test public void testNoCaptureConversionDuringDetectingSupertypesDeepInHierarchy() { doTest(); }
  @Test public void testLowerBoundAssignabilityCheck() { doTest(); }
  @Test public void testIgnoreErasureForProperTypeBound() { doTest(); }
  @Test public void testInferenceErrorAttribution() {doTest();}
  @Test public void testLocalClassParameters() {doTest();}
  @Test public void testRawAtFBoundAtNew() { doTest(); }
  @Test public void testUnboxingTrivialGeneric(){doTest();}
  
  @TestFor(issues = "IDEA-378878")
  @Test public void testWildcardAfterMethodRef() { doTest(); }

  @TestFor(issues = "IDEA-385574")
  @Test public void testIDEA385574(){ doTest(); }

  @TestFor(issues = "IDEA-386630")
  @Test public void testIDEA386630(){ doTest(); }

  @Test public void testWildcardContainmentWithTypeParameterBound(){ doTest(); }

  @Test public void testWildcardContainmentWithTypeVariableBound(){ doTest(); }

  @Test public void testWildcardContainmentUncheckedConversion(){ doTest(true); }

  @Test public void testMethodRefOnRawFBoundedReceiver(){ doTest(); }
}
