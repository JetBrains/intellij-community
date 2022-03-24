// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

//javac option to dump bounds: -XDdumpInferenceGraphsTo=
public class GenericsHighlighting8Test extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting8";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
    enableInspectionTool(new UnusedImportInspection());
    enableInspectionTool(new JavacQuirksInspection());
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection()};
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

  public void testIDEA21597() {
    doTest();
  }
  public void testIDEA20573() {
    doTest();
  }
  public void testIDEA20244() {
    doTest();
  }
  public void testIDEA22005() {
    doTest();
  }
  public void testIDEA57259() {
    doTest();
  }
  public void testIDEA107957() {
    doTest();
  }
  public void testIDEA109875() {
    doTest();
  }
  public void testIDEA106964() {
    doTest();
  }
  public void testIDEA107782() {
    doTest();
  }
  public void testInheritedWithDifferentArgsInTypeParams() {
    doTest();
  }
  public void testIllegalForwardReferenceInTypeParameterDefinition() {
    doTest();
  }

  public void testIDEA57877() {
    doTest();
  }
  public void testIDEA110568() {
    doTest();
  }
  public void testSelfRef() {
    doTest();
  }
  public void testTypeParamsCyclicInference() {
    doTest();
  }
  public void testCaptureTopLevelWildcardsForConditionalExpression() {
    doTest();
  }
  public void testGenericsOverrideMethodInRawInheritor() {
    doTest();
  }

  public void testIDEA107654() {
    doTest();
  }

  public void testIDEA55510() {
    doTest(true);
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

  public void testIDEA108287() {
    doTest(true);
  }

  public void testIDEA77128() {
    doTest();
  }

  public void testDisableCastingToNestedWildcards() {
    doTest();
  }

  public void testBooleanInferenceFromIfCondition() {
    doTest();
  }

  public void testMethodCallOnRawTypesExtended() {
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
  public void testRawAssignments() {
    doTest();
  }
  public void testIDEA87860() {
    doTest();
  }

  public void testIDEA114797() {
    doTest();
  }

  public void testCastToIntersectionType() {
    doTest();
  }

  public void testCastToIntersection() {
    doTest();
  }

  public void testIDEA122401() {
    doTest();
  }

  public void testCaptureInsideNestedCalls() {
    doTest();
  }

  public void testSuperWildcardWithBoundPromotion() { doTest();}

  public void testErasure() { doTest(); }

  public void testWildcardBoundsCombination() {
    doTest();
  }

  public void testIDEA128333() {
    doTest();
  }

  public void testIDEA78402() { doTest(); }

  public void testUncheckedWarningInsideLambdaReturnStatement() {
    doTest(true);
  }

  public void testInferredParameterInBoundsInRecursiveGenerics() {
    doTest(false);
  }

  public void testSuperWildcardCapturedSuperExtendsWildcardCapturedExtends() {
    doTest(false);
  }

  public void testRejectContradictingEqualsBounds() {
    doTest(false);
  }

  public void testRejectEqualsBoundsContradictingLowerBound() {
    doTest(false);
  }

  public void testSuperInterfaceMethodCalledByMatterOfInterface() {
    doTest(false);
  }

  private void doTest() {
    doTest(false);
  }

   private void doTest(boolean warnings) {
     LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
     IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
     doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
   }


  public void testIDEA67584() {
    doTest();
  }
  public void testIDEA113225() {
    doTest();
  }

  public void testIDEA139069() {
    doTest();
  }

  public void testIDEA67745() {
    doTest();
  }

  public void testIDEA57313() {
    doTest();
  }

  public void testIDEA57387() {
    doTest();
  }

  public void testIDEA57314() {
    doTest();
  }

  public void testIDEA57322() {
    doTest();
  }

  public void testIDEA57362() {
    doTest();
  }

  public void testIDEA57320() {
    doTest();
  }

  public void testIDEA139090() {
    doTest();
  }

  public void testIDEA57502() {
    doTest();
  }

  public void testIDEA67746() {
    doTest();
  }

  public void testIDEA67592() {
    doTest();
  }

  public void testIDEA93713() {
    doTest();
  }

  public void testIDEA107713() {
    doTest();
  }

  public void testExceptionCollectionWithLambda() {
    doTest();
  }

  public void testUncheckedWarningsWhenInferredTypeLeadsToRawRoGenericAssignment() {
    doTest(true);
  }

  public void testExpectedTypeBasedOnArrayCreationWithoutExplicitType() {
    doTest();
  }

  public void testIDEA148348() {
    doTest();
  }

  public void testIDEA148361() {
    doTest();
  }

  public void testIDEA134059() {
    doTest();
  }

  public void testIDEA139222() {
    doTest();
  }

  public void testIDEA139156() {
    doTest();
  }

  public void testIDEA139169() {
    doTest();
  }

  public void testIDEA131686() {
    doTest();
  }

  public void testIDEA56754() {
    doTest();
  }

  public void testAccessClassForWildcardCaptureType() {
    doTest();
  }

  public void testDistinguishTypeArgs() {
    doTest();
  }

  public void testRecursiveCapturedWildcardTypes() {
    doTest();
  }

  public void testRecursiveCapturedWildcardTypesIDEA139167() {
    doTest();
  }
  
  public void testRecursiveCapturedWildcardTypesIDEA139157() {
    doTest();
  }

  public void testIDEA146897() {
    doTest();
  }

  public void testIDEA139096() {
    doTest();
  }

  public void testCastingCapturedWildcardToPrimitive() {
    doTest();
  }

  public void testCastingCapturedWildcardToArray() {
    doTest();
  }

  public void testCheckUncheckedAssignmentDuringVariablesResaolution() {
    doTest(true);
  }

  public void testRetrieveInferenceErrorsFromContainingCallsIfCurrentDoesNotProvideAny() {
    doTest();
  }

  public void testForeachOverCapturedWildcardWithCollectionUpperBound() {
    doTest();
  }

  public void testCapturedWildcardWithPrimitiveTypesChecks() {
    doTest();
  }

  public void testCapturedWildcardPackageLocalAccess() {
    doTest();
  }

  public void testCapturedWildcardPassedThroughMethodCallChain() {
    doTest();
  }

  public void testIDEA152179() {
    doTest();
  }

  public void testLooseInvocationContextForProperPrimitiveTypes() {
    doTest();
  }

  public void testUncheckedWarningsInsideIncorporationPhase() {
    doTest();
  }

  public void testUnifiedSubstitutorUpInTheHierarchy() {
    doTest();
  }

  public void testNestedCaptures() {
    doTest();
  }

  public void testErasureOfReturnTypeOfNonGenericMethod() {
    doTest();
  }

  public void testUncheckedCastWithCapturedWildcards() {
    doTest(true);
  }

  public void testReifiableCapturedWildcards() {
    doTest(true);
  }

  public void testUncheckedWarningsInsideLambda() {
    doTest(true);
  }

  public void testLowerBoundOfCapturedWildcardInSubtypingConstraint() {
    doTest(true);
  }

  public void testMembersContainedInCapturedWildcardType() {
    doTest();
  }

  public void testArrayTypeUpperBound() { doTest(); }

  public void testNonGenericInnerOfGenericOuter() { doTest(); }
  public void testIDEA53597() { doTest(); }

  public void testTypeParameterBoundsWithSubstitutionWhenMethodHierarchyIsChecked() {
    doTest();
  }

  public void testBoundsPromotionForDerivedType() {
    doTest();
  }

  public void testSameErasureForStaticMethodsInInterfaces() {
    doTest();
  }

  public void testConditionalExpressionInIncompleteCall() {
    doTest();
  }
  
  public void testIDEA194093 () {
    doTest();
  }

  public void testTooltipTypesAgree() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String expected = "<html><body><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: " + greyed + "'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'><font color=\"" + toolTipForeground + "\">Generic</font></td><td style='padding: 0px 0px 8px 0px;'>&lt;<font color=\"" + toolTipForeground + "\">? extends Number</font>,</td><td style='padding: 0px 0px 8px 0px;'><font color=\"" + toolTipForeground + "\">Number</font>,</td><td style='padding: 0px 0px 8px 0px;'><font color=\"" + toolTipForeground + "\">Integer</font>&gt;</td></tr>" +
                      "<tr><td style='padding: 0px 16px 0px 4px;color: " + greyed + "'>Provided:</td>" +
                      "<td style='padding: 0px 4px 0px 0px;'><font color=\"" + toolTipForeground + "\">Generic</font></td><td style='padding: 0px 0px 0px 0px;'>&lt;<font color=\"" + toolTipForeground + "\">Integer</font>,</td><td style='padding: 0px 0px 0px 0px;'><font color=\"" + red + "\">Integer</font>,</td><td style='padding: 0px 0px 0px 0px;'><font color=\"" + toolTipForeground + "\">Integer</font>&gt;</td></tr>" +
                      "</table>" +
                      "</body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testVarargsTooltip() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String expected = "<html><body><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: " + greyed+ "'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'><font color=\"" + toolTipForeground + "\">String</font></td>" +
                      "</tr>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 0px 4px;color: " + greyed + "'>Provided:</td>" +
                      "<td style='padding: 0px 4px 0px 0px;'><font color=\"" + red + "\">int</font></td>" +
                      "</tr>" +
                      "</table></body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testVarargsTooltip2() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String paramBgColor = ColorUtil.toHtmlColor(EditorColorsUtil.getGlobalOrDefaultColorScheme()
                                                  .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
                                                  .getBackgroundColor());
    int fontSize = StartupUiUtil.getLabelFont().getSize() - (SystemInfo.isWindows ? 0 : 1);
    String expected = "<html><body><table>" +
                      "<tr><td/><td style=\"color: " + greyed + "; padding-left: 16px; padding-right: 24px;\">Required type</td>" +
                      "<td style=\"color: " + greyed + "; padding-right: 28px;\">Provided</td></tr>" +
                      "<tr><td><table><tr><td style=\"color: " + greyed + "; font-size:" + fontSize + "pt; padding:1px 4px 1px 4px;background-color: " + paramBgColor + ";\">list:</td></tr></table></td>" +
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><font color=\"" + toolTipForeground + "\">String...</font></td>" +
                      "<td style=\"padding-right: 28px;\"><font color=\"" + red + "\">int</font></td></tr>" +
                      "<tr><td/><td style=\"padding-left: 16px; padding-right: 24px;\"/><td style=\"padding-right: 28px;\"><font color=\"" + red + "\">int</font></td></tr>" +
                      "</table></body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testTooltipShortTypeNames() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String paramBgColor = ColorUtil.toHtmlColor(EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
      .getBackgroundColor());
    int fontSize = StartupUiUtil.getLabelFont().getSize() - (SystemInfo.isWindows ? 0 : 1);
    String expected = "<html><body><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: " + greyed + "'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'><font color=\"" + toolTipForeground + "\">CharSequence</font></td>" +
                      "</tr>" +
                      "<tr><td style='padding: 0px 16px 0px 4px;color: " + greyed + "'>Provided:</td>" +
                      "<td style='padding: 0px 4px 0px 0px;'><font color=\"" + red + "\">int</font></td></tr>" +
                      "</table></body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testTooltipWithCapture() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String expected = "<html><body><table>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 8px 4px;color: " + greyed + "'>Required type:</td>" +
                      "<td style='padding: 0px 4px 8px 0px;'><font color=\"" + toolTipForeground + "\">Class</font></td>" +
                      "<td style='padding: 0px 0px 8px 0px;'>&lt;<font color=\"" + toolTipForeground + "\">capture of ?</font>&gt;</td>" +
                      "</tr>" +
                      "<tr>" +
                      "<td style='padding: 0px 16px 0px 4px;color: " + greyed + "'>Provided:</td>" +
                      "<td style='padding: 0px 4px 0px 0px;'><font color=\"" + toolTipForeground + "\">Class</font></td>" +
                      "<td style='padding: 0px 0px 0px 0px;'>&lt;<font color=\"" + red + "\">capture of ?</font>&gt;</td></tr>" +
                      "</table></body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testTooltipComponents() {
    doTest();
    String toolTipForeground = ColorUtil.toHtmlColor(UIUtil.getToolTipForeground());
    String greyed = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground());
    String red = ColorUtil.toHtmlColor(UIUtil.getErrorForeground());
    String paramBgColor = ColorUtil.toHtmlColor(EditorColorsUtil.getGlobalOrDefaultColorScheme()
      .getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
      .getBackgroundColor());
    int fontSize = StartupUiUtil.getLabelFont().getSize() - (SystemInfo.isWindows ? 0 : 1);
    String expected = "<html><body><table>" +
                      "<tr>" +
                      "<td/>" +
                      "<td style=\"color: " + greyed + "; padding-left: 16px; padding-right: 24px;\">Required type</td>" +
                      "<td style=\"color: " + greyed + "; padding-right: 28px;\">Provided</td></tr>" +
                      "<tr>" +
                      "<td><table><tr><td style=\"color: " + greyed + "; font-size:" + fontSize + "pt; padding:1px 4px 1px 4px;background-color: " + paramBgColor + ";\">integerList:</td></tr></table></td>" + 
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><font color=\"" + toolTipForeground + "\">List&lt;Integer&gt;</font></td>" +
                      "<td style=\"padding-right: 28px;\"><font color=\"" + toolTipForeground + "\">List</font>&lt;<font color=\"" + red + "\">String</font>&gt;</td></tr>" +
                      "<tr>" +
                      "<td><table><tr><td style=\"color: " + greyed + "; font-size:" + fontSize + "pt; padding:1px 4px 1px 4px;background-color: " + paramBgColor + ";\">stringList:</td></tr></table></td>" + 
                      "<td style=\"padding-left: 16px; padding-right: 24px;\"><font color=\"" + toolTipForeground+ "\">List&lt;String&gt;</font></td>" +
                      "<td style=\"padding-right: 28px;\"><font color=\"" + toolTipForeground + "\">List</font>&lt;<font color=\"" + red + "\">Integer</font>&gt;</td></tr>" +
                      "</table></body></html>";

    doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals(expected, info.getToolTip()));
  }

  public void testBridgeMethodOverriding() { doTest(); }
  public void testClassLiteralType() { doTest(); }
  public void testNestedWildcardsWithImplicitBounds() { doTest(); }
  public void testCallOnRawWithExplicitTypeArguments() { doTest(); }
  public void testNoCaptureConversionDuringDetectingSupertypesDeepInHierarchy() { doTest(); }
  public void testLowerBoundAssignabilityCheck() { doTest(); }
  public void testIgnoreErasureForProperTypeBound() { doTest(); }
}
