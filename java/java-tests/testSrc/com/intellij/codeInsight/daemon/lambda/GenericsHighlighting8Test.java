/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

//javac option to dump bounds: -XDdumpInferenceGraphsTo=
public class GenericsHighlighting8Test extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting8";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection(), new UnusedImportLocalInspection()};
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }

  public void testReferenceTypeParams() {
    doTest();
  }
  public void testTypeParameterBoundsList() {
    doTest();
  }
  public void testClassInheritance() {
    doTest();
  }
  public void testTypeInference() {
    doTest();
  }
  public void testRaw() {
    doTest(true);
  }
  public void testExceptions() {
    doTest();
  }
  public void testExplicitMethodParameters() {
    doTest();
  }
  public void testInferenceWithBounds() {
    doTest();
  }
  public void testInferenceWithSuperBounds() {
    doTest();
  }
  public void testInferenceWithUpperBoundPromotion() {
    doTest();
  }
  public void testVariance() {
    doTest();
  }
  public void testForeachTypes() {
    doTest();
  }
  public void testRawOverridingMethods() {
    doTest();
  }    
  public void testAutoboxing() {
    doTest();
  }                
  public void testAutoboxingMethods() {
    doTest();
  }                                             
  public void testAutoboxingConstructors() {
    doTest();
  }                                      
  public void testEnumWithAbstractMethods() {
    doTest();
  }                                   
  public void testEnum() { doTest(); }
  public void testEnum56239() {
    doTest();
  }
  public void testSameErasure() {
    doTest();
  }          
  public void testMethods() {
    doTest();
  }            
  public void testFields() {
    doTest();
  }           
  public void testStaticImports() {
    doTest(true);
  }   
  public void testUncheckedCasts() {
    doTest(true);
  }
  public void testUncheckedOverriding() {
    doTest(true);
  }
  public void testWildcardTypes() {
    doTest(true);
  }
  public void testConvertibleTypes() {
    doTest(true);
  }
  public void testIntersectionTypes() {
    doTest(true);
  }
  public void testVarargs() {
    doTest(true);
  }
  public void testTypeArgsOnRaw() {
    doTest();
  }          
  public void testConditionalExpression() {
    doTest();
  }  
  public void testUnused() {
    doTest(true);
  }            
  public void testIDEADEV7337() {
    doTest(true);
  }      
  public void testIDEADEV10459() {
    doTest(true);
  }     
  public void testIDEADEV12951() {
    doTest(true);
  }     
  public void testIDEADEV13011() {
    doTest(true);
  }     
  public void testIDEADEV14006() {
    doTest(true);
  }     
  public void testIDEADEV14103() {
    doTest(true);
  }        
  public void testIDEADEV15534() {
    doTest(true);
  }      
  public void testIDEADEV23157() {
    doTest(true);
  }    
  public void testIDEADEV24166() {
    doTest(true);
  }  
  public void testIDEADEV57343() {
    doTest();
  }
  public void testSOE() {
    doTest(true);
  }        
  public void testGenericExtendException() {
    doTest();
  }  
  public void testSameErasureDifferentReturnTypes() {
    doTest();
  }
  public void testDeepConflictingReturnTypes() {
    doTest();
  }  
  public void testInheritFromTypeParameter() {
    doTest();
  }     
  public void testAnnotationsAsPartOfModifierList() {
    doTest();
  } 
  public void testImplementAnnotation() {
    doTest();
  }      
  public void testOverrideAtLanguageLevel6() {
    doTest();
  }    
  public void testSuperMethodCallWithErasure() {
    doTest();
  } 
  public void testWildcardCastConversion() {
    doTest();
  }    
  public void testTypeWithinItsWildcardBound() {
    doTest();
  } 
  public void testMethodSignatureEquality() {
    doTest();
  }    
  public void testInnerClassRef() {
    doTest();
  }             
  public void testPrivateInnerClassRef() {
    doTest();
  }      
  public void testWideningCastToTypeParam() {
    doTest();
  }     
  public void testCapturedWildcardAssignments() {
    doTest();
  }        
  public void testTypeParameterBoundVisibility() {
    doTest();
  }
  public void testUncheckedWarningsLevel6() {
    doTest(true);
  }
  public void testIDEA77991() {
    doTest();
  }                
  public void testIDEA80386() {
    doTest();
  }               
  public void testIDEA66311() {
    doTest();
  }  
  public void testIDEA67672() {
    doTest();
  }  
  public void testIDEA88895() {
    doTest();
  }  
  public void testIDEA67667() {
    doTest();
  }  
  public void testIDEA66311_16() {
    doTest();
  }
  public void testIDEA76283() {
    doTest();
  }
  public void testIDEA74899() {
    doTest();
  }  
  public void testIDEA63291() {
    doTest();
  }  
  public void testIDEA72912() {
    doTest();
  }         
  public void testIllegalGenericTypeInInstanceof() {
    doTest();
  } 
  public void testIDEA57339() {
    doTest();
  } 
  public void testIDEA57340() {
    doTest();
  }
  public void testIDEA89771() {
    doTest();
  }
  public void testIDEA89801() {
    doTest();
  }
  public void testIDEA67681() {
    doTest();
  }
  public void testIDEA67599() {
    doTest();
  }
  public void testIDEA57668() {
    doTest();
  }
  public void testIDEA57667() {
    doTest();
  }
  public void testIDEA57650() {
    doTest();
  }
  public void testIDEA57378() {
    doTest();
  }
  public void testIDEA57557() {
    doTest();
  }
  public void testIDEA57563() {
    doTest();
  }
  public void testIDEA57275() {
    doTest();
  }
  public void testIDEA57533() {
    doTest();
  }
  public void testIDEA57509() {
    doTest();
  }
  public void testIDEA57410() {
    doTest();
  }
  public void testIDEA57411() {
    doTest();
  }
  public void testIDEA57484() {
    doTest();
  }
  public void testIDEA57485() {
    doTest();
  }
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
  public void testIDEA57495() {
    doTest();
  }
  public void testIDEA57494() {
    doTest();
  }
  public void testIDEA57496() {
    doTest();
  }
  public void testIDEA57264() {
    doTest();
  }
  public void testIDEA57315() {
    doTest();
  }
  public void testIDEA57346() {
    doTest();
  }
  public void testIDEA57284() {
    doTest();
  }
  public void testIDEA57286() {
    doTest();
  }
  public void testIDEA57307() {
    doTest(true);
  }
  public void testIDEA57308() {
    doTest();
  }
  public void testIDEA57310() {
    doTest();
  }
  public void testIDEA57311() {
    doTest();
  }
  public void testIDEA57309() {
    doTest();
  }
  public void testIDEA90802() {
    doTest();
  }
  public void testIDEA70370() {
    doTest(true);
  }
  public void testInaccessibleThroughWildcard() {
    doTest();
  }
  public void testInconvertibleTypes() {
    doTest();
  }
  public void testIncompatibleReturnType() {
    doTest();
  }
  public void testContinueInferenceAfterFirstRawResult() {
    doTest();
  }
  public void testDoNotAcceptLowerBoundIfRaw() {
    doTest();
  }
  public void testStaticOverride() {
    doTest();
  }
  public void testTypeArgumentsGivenOnRawType() {
    doTest();
  }
  public void testSelectFromTypeParameter() {
    doTest();
  }
  public void testTypeArgumentsGivenOnAnonymousClassCreation() {
    doTest();
  }

  public void testIDEA94011() {
    doTest();
  }
  public void testDifferentTypeParamsInOverloadedMethods() {
    doTest(true);
  }

  public void testIDEA91626() {
    doTest(true);
  }
  public void testIDEA92022() {
    doTest();
  }
  public void testRawOnParameterized() {
    doTest();
  }
  public void testFailedInferenceWithBoxing() {
    doTest();
  }
  public void testFixedFailedInferenceWithBoxing() {
    doTest();
  }
  public void testInferenceWithBoxingCovariant() {
    doTest();
  }
  public void testSuperWildcardIsNotWithinItsBound() {
    doTest();
  }
  public void testSpecificReturnType() {
    doTest();
  }
  public void testParameterizedParameterBound() {
    doTest();
  }
  public void testInstanceClassInStaticContextAccess() {
    doTest();
  }
  public void testFlattenIntersectionType() {
    doTest();
  }
  public void testIDEA97276() {
    doTest();
  }
  public void testWildcardsBoundsIntersection() {
    doTest();
  }
  public void testOverrideWithMoreSpecificReturn() {
    doTest();
  }
  public void testIDEA97888() {
    doTest();
  }
  public void testMethodCallParamsOnRawType() {
    doTest();
  }
  public void testIDEA98421() {
    doTest();
  }
  public void testErasureTypeParameterBound() {
    doTest();
  }
  public void testThisAsAccessObject() {
    doTest();
  }
  public void testIDEA67861() {
    doTest();
  }
  public void testIDEA67597() {
    doTest();
  }
  public void testIDEA57539() {
    doTest();
  }
  public void testIDEA67570() {
    doTest();
  }
  public void testIDEA99061() {
    doTest();
  }
  public void testIDEA99347() {
    doTest();
  }
  public void testIDEA86875() {
    doTest();
  }
  public void testIDEA103760(){
    doTest();
  }
  public void testIDEA105846(){
    doTest();
  }
  public void testIDEA105695(){
    doTest();
  }
  public void testIDEA104992(){
    doTest();
  }
  public void testIDEA57446(){
    doTest();
  }
  public void testIDEA67677(){
    doTest();
  }
  public void testIDEA67798(){
    doTest();
  }
  public void testIDEA57534(){
    doTest();
  }
  public void testIDEA57482(){
    doTest();
  }
  public void testIDEA67577(){
    doTest();
  }
  public void testIDEA57413(){
    doTest();
  }
  public void testIDEA57265(){
    doTest();
  }
  public void testIDEA57271(){
    doTest();
  }
  public void testIDEA57272(){
    doTest();
  }
  public void testIDEA57285(){
    doTest();
  }
  public void testIDEA65066(){
    doTest();
  }
  public void testIDEA67998(){
    doTest();
  }
  public void testIDEA18425(){
    doTest();
  }
  public void testIDEA27080(){
    doTest();
  }
  public void testIDEA22079(){
    doTest();
  }
  public void testIDEA21602(){
    doTest();
  }
  public void testIDEA21602_7(){
    doTest();
  }

  public void testIDEA21597() throws Exception {
    doTest();
  }
  public void testIDEA20573() throws Exception {
    doTest();
  }
  public void testIDEA20244() throws Exception {
    doTest();
  }
  public void testIDEA22005() throws Exception {
    doTest();
  }
  public void testIDEA57259() throws Exception {
    doTest();
  }
  public void testIDEA107957() throws Exception {
    doTest();
  }
  public void testIDEA109875() throws Exception {
    doTest();
  }
  public void testIDEA106964() throws Exception {
    doTest();
  }
  public void testIDEA107782() throws Exception {
    doTest();
  }
  public void testInheritedWithDifferentArgsInTypeParams() throws Exception {
    doTest();
  }
  public void testIllegalForwardReferenceInTypeParameterDefinition() throws Exception {
    doTest();
  }

  public void testIDEA57877() throws Exception {
    doTest();
  }
  public void testIDEA110568() throws Exception {
    doTest();
  }
  public void testSelfRef() throws Exception {
    doTest();
  }
  public void testTypeParamsCyclicInference() throws Exception {
    doTest();
  }
  public void testCaptureTopLevelWildcardsForConditionalExpression() throws Exception {
    doTest();
  }
  public void testGenericsOverrideMethodInRawInheritor() throws Exception {
    doTest();
  }

  public void testIDEA107654() throws Exception {
    doTest();
  }

  public void testIDEA55510() throws Exception {
    doTest();
  }

  public void testIDEA27185(){
    doTest();
  }
  public void testIDEA67571(){
    doTest();
  }
  public void testTypeArgumentsOnRawType(){
    doTest();
  }

  public void testTypeArgumentsOnRawType17(){
    doTest();
  }

  public void testWildcardsOnRawTypes() {
    doTest();
  }
  public void testDisableWithinBoundsCheckForSuperWildcards() {
    doTest();
  }

  public void testIDEA108287() throws Exception {
    doTest();
  }

  public void testIDEA77128() throws Exception {
    doTest();
  }

  public void testDisableCastingToNestedWildcards() throws Exception {
    doTest();
  }

  public void testBooleanInferenceFromIfCondition() throws Exception {
    doTest();
  }

  public void testMethodCallOnRawTypesExtended() throws Exception {
    doTest();
  }

  public void testIDEA104100() {
    doTest();
  }
  public void testIDEA104160() {
    doTest();
  }
  public void testSOEInLeastUpperClass() {
    doTest();
  }

  public void testIDEA57334() {
    doTest();
  }

  public void testIDEA57325() {
    doTest();
  }
  public void testIDEA67835() {
    doTest();
  }
  public void testIDEA67744() {
    doTest();
  }
  public void testIDEA67682() {
    doTest();
  }
  public void testIDEA57391() {
    doTest();
  }
  public void testIDEA110869() {
    doTest();
  }
  public void testIDEA110947() { doTest(false); }
  public void testIDEA112122() {
    doTest();
  }
  public void testNoInferenceFromTypeCast() {
    doTest();
  }
  public void testCaptureWildcardsInTypeCasts() {
    doTest();
  }
  public void testIDEA111085() {
    doTest();
  }
  public void testIDEA109556() {
    doTest();
  }
  public void testIDEA107440() {
    doTest();
  }
  public void testIDEA57289() {
    doTest();
  }
  public void testIDEA57439() {
    doTest();
  }
  public void testIDEA57312() {
    doTest();
  }
  public void testIDEA67865() {
    doTest();
  }
  public void testBoxingSpecific() {
    doTest();
  }
  public void testIDEA67843() {    //fixme need to change test
    doTest();
  }
  public void testAmbiguousTypeParamVsConcrete() {
    doTest();
  }
  public void testRawAssignments() throws Exception {
    doTest();
  }
  public void testIDEA87860() throws Exception {
    doTest();
  }

  public void testIDEA114797() throws Exception {
    doTest();
  }

  public void testCastToIntersectionType() throws Exception {
    doTest();
  }

  public void testCastToIntersection() throws Exception {
    doTest();
  }

  public void testIDEA122401() throws Exception {
    doTest();
  }

  public void testCaptureInsideNestedCalls() throws Exception {
    doTest();
  }

  public void testSuperWildcardWithBoundPromotion() { doTest();}

  public void testErasure() throws Exception { doTest(); }

  public void testWildcardBoundsCombination() throws Exception {
    doTest();
  }

  public void testIDEA128333() throws Exception {
    doTest();
  }

  public void testIDEA78402() { doTest(); }

  public void testUncheckedWarningInsideLambdaReturnStatement() throws Exception {
    doTest(true);
  }

  public void testInferredParameterInBoundsInRecursiveGenerics() {
    doTest(false);
  }

  public void testSuperWildcardCapturedSuperExtendsWildcardCapturedExtends() throws Exception {
    doTest(false);
  }

  public void testRejectContradictingEqualsBounds() throws Exception {
    doTest(false);
  }

  public void testRejectEqualsBoundsContradictingLowerBound() throws Exception {
    doTest(false);
  }

  public void testSuperInterfaceMethodCalledByMatterOfInterface() throws Exception {
    doTest(false);
  }

  private void doTest() {
    doTest(false);
  }

   private void doTest(boolean warnings) {
     LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
     IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), myTestRootDisposable);
     doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
   }


  public void testIDEA67584() throws Exception {
    doTest();
  }
  public void testIDEA113225() throws Exception {
    doTest();
  }

  public void testIDEA139069() throws Exception {
    doTest();
  }

  public void testIDEA67745() throws Exception {
    doTest();
  }

  public void testIDEA57313() throws Exception {
    doTest();
  }

  public void testIDEA57387() throws Exception {
    doTest();
  }

  public void testIDEA57314() throws Exception {
    doTest();
  }

  public void testIDEA57322() throws Exception {
    doTest();
  }

  public void testIDEA57362() throws Exception {
    doTest();
  }

  public void testIDEA57320() throws Exception {
    doTest();
  }

  public void testIDEA139090() throws Exception {
    doTest();
  }

  public void testIDEA57502() throws Exception {
    doTest();
  }

  public void testIDEA67746() throws Exception {
    doTest();
  }

  public void testIDEA67592() throws Exception {
    doTest();
  }

  public void testIDEA93713() throws Exception {
    doTest();
  }

  public void testIDEA107713() throws Exception {
    doTest();
  }

  public void testExceptionCollectionWithLambda() throws Exception {
    doTest();
  }

  public void testUncheckedWarningsWhenInferredTypeLeadsToRawRoGenericAssignment() throws Exception {
    doTest(true);
  }

  public void testExpectedTypeBasedOnArrayCreationWithoutExplicitType() throws Exception {
    doTest();
  }

  public void testIDEA148348() throws Exception {
    doTest();
  }

  public void testIDEA148361() throws Exception {
    doTest();
  }

  public void testIDEA134059() throws Exception {
    doTest();
  }

  public void testIDEA139222() throws Exception {
    doTest();
  }

  public void testIDEA139156() throws Exception {
    doTest();
  }

  public void testIDEA139169() throws Exception {
    doTest();
  }

  public void testIDEA131686() throws Exception {
    doTest();
  }

  public void testIDEA56754() throws Exception {
    doTest();
  }

  public void testAccessClassForWildcardCaptureType() throws Exception {
    doTest();
  }

  public void testDistinguishTypeArgs() throws Exception {
    doTest();
  }

  public void testRecursiveCapturedWildcardTypes() throws Exception {
    doTest();
  }

  public void testRecursiveCapturedWildcardTypesIDEA139167() throws Exception {
    doTest();
  }
  
  public void testRecursiveCapturedWildcardTypesIDEA139157() throws Exception {
    doTest();
  }

  public void testIDEA146897() throws Exception {
    doTest();
  }

  public void testIDEA139096() throws Exception {
    doTest();
  }

  public void testCastingCapturedWildcardToPrimitive() throws Exception {
    doTest();
  }

  public void testCastingCapturedWildcardToArray() throws Exception {
    doTest();
  }

  public void testCheckUncheckedAssignmentDuringVariablesResaolution() throws Exception {
    doTest(true);
  }

  public void testRetrieveInferenceErrorsFromContainingCallsIfCurrentDoesNotProvideAny() throws Exception {
    doTest();
  }

  public void testForeachOverCapturedWildcardWithCollectionUpperBound() throws Exception {
    doTest();
  }

  public void testCapturedWildcardWithPrimitiveTypesChecks() throws Exception {
    doTest();
  }

  public void testCapturedWildcardPackageLocalAccess() throws Exception {
    doTest();
  }

  public void testCapturedWildcardPassedThroughMethodCallChain() throws Exception {
    doTest();
  }

  public void testIDEA152179() throws Exception {
    doTest();
  }

  public void testLooseInvocationContextForProperPrimitiveTypes() throws Exception {
    doTest();
  }

  public void testUncheckedWarningsInsideIncorporationPhase() throws Exception {
    doTest();
  }

  public void testUnifiedSubstitutorUpInTheHierarchy() throws Exception {
    doTest();
  }

  public void testNestedCaptures() throws Exception {
    doTest();
  }

  public void testErasureOfReturnTypeOfNonGenericMethod() throws Exception {
    doTest();
  }
}
