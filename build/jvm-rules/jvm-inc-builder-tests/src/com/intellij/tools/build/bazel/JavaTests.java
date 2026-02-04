package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.Test;

// Migrated from JPS-based test engine
public class JavaTests extends BazelIncBuildTest {
  @Test
  public void testInner() throws Exception {
    performTest("java/common/inner").assertFailure();
  }

  @Test
  public void testAnonymous() throws Exception {
    performTest("java/common/anonymous").assertFailure();
  }

  @Test
  public void testDeleteClass() throws Exception {
    performTest("java/common/deleteClass").assertFailure();
  }

  @Test
  public void testDeleteClass1() throws Exception {
    performTest("java/common/deleteClass1").assertFailure();
  }

  @Test
  public void testDeleteClass2() throws Exception {
    performTest("java/common/deleteClass2").assertFailure();
  }

  @Test
  public void testDeleteInnerClass() throws Exception {
    performTest("java/common/deleteInnerClass").assertFailure();
  }

  @Test
  public void testDeleteClass3() throws Exception {
    performTest("java/common/deleteClass3").assertSuccessful();
  }

  @Test
  public void testDeleteClass4() throws Exception {
    performTest("java/common/deleteClass4").assertFailure();
  }

  @Test
  public void testDeleteInnerClass1() throws Exception {
    performTest("java/common/deleteInnerClass1").assertFailure();
  }

  @Test
  public void testClass2Interface1() throws Exception {
    performTest("java/common/class2Interface1").assertSuccessful();
  }

  @Test
  public void testClass2Interface2() throws Exception {
    performTest("java/common/class2Interface2").assertSuccessful();
  }

  @Test
  public void testClass2Interface3() throws Exception {
    performTest("java/common/class2Interface3").assertSuccessful();
  }

  @Test
  public void testDependencyUpdate() throws Exception {
    performTest("java/common/dependencyUpdate").assertSuccessful();
  }

  @Test
  public void testNoSecondFileCompile() throws Exception {
    performTest("java/common/noSecondFileCompile").assertSuccessful();
  }

  @Test
  public void testNoSecondFileCompile1() throws Exception {
    performTest("java/common/noSecondFileCompile1").assertSuccessful();
  }

  @Test
  public void testChangeDefinitionToClass() throws Exception {
    performTest("java/common/changeDefinitionToClass").assertSuccessful();
  }

  @Test
  public void testDeleteImportedClass() throws Exception {
    performTest("java/common/deleteImportedClass").assertFailure();
  }

  @Test
  public void testChangeDefinitionToClass2() throws Exception {
    performTest("java/common/changeDefinitionToClass2").assertSuccessful();
  }

  @Test
  public void testMoveToplevelClassToAnotherFile() throws Exception {
    performTest("java/common/moveToplevelClassToAnotherFile").assertSuccessful();
  }

  @Test
  public void testDeleteClassPackageDoesntMatchRoot() throws Exception {
    performTest("java/common/deleteClassPackageDoesntMatchRoot").assertFailure();
  }

  @Test
  public void testDeletePermittedClass() throws Exception {
    performTest("java/common/deletePermittedClass").assertFailure();
  }

  @Test
  public void testDeleteSealedPermission() throws Exception {
    performTest("java/common/deleteSealedPermission").assertFailure();
  }

  @Test
  public void testAddClass() throws Exception {
    performTest("java/common/addClass").assertFailure();
  }

  @Test
  public void testAddDuplicateClass() throws Exception {
    performTest("java/common/addDuplicateClass").assertFailure();
  }

  @Test
  public void testAddClassHidingImportedClass() throws Exception {
    performTest("java/common/addClassHidingImportedClass").assertSuccessful();
  }

  @Test
  public void testAddClassHidingImportedClass2() throws Exception {
    performTest("java/common/addClassHidingImportedClass2").assertSuccessful();
  }

  @Test
  public void testConflictingClasses() throws Exception {
    performTest("java/common/conflictingClasses").assertSuccessful();
  }

  @Test
  public void testCompileDependenciesOnMovedClassesInFirstRound() throws Exception {
    performTest("java/common/compileDependenciesOnMovedClassesInFirstRound").assertSuccessful();
  }

  @Test
  public void testDeleteClassAfterCompileErrors() throws Exception {
    performTest(2, "java/common/deleteClassAfterCompileErrors").assertFailure();
  }

  @Test
  public void testNothingChanged() throws Exception {
    performTest("java/common/nothingChanged").assertSuccessful();
  }

  @Test
  public void testMoveClassToAnotherRoot() throws Exception {
    performTest("java/common/moveClassToAnotherRoot").assertSuccessful();
  }

  @Test
  public void testSameClassesInDifferentModules() throws Exception {
    performTest("java/common/sameClassesInDifferentModules").assertSuccessful();
  }

  @Test
  public void testDontMarkDependentsAfterCompileErrors() throws Exception {
    performTest(2, "java/common/dontMarkDependentsAfterCompileErrors").assertFailure();
  }

  @Test
  public void testMoveClassToDependentModule() throws Exception {
    performTest("java/common/moveClassToDependentModule").assertSuccessful();
  }

  @Test
  public void testIntegrateOnSuperclassRemovedAndRestored() throws Exception {
    performTest(2, "java/common/integrateOnSuperclassRemovedAndRestored").assertSuccessful();
  }

  @Test
  public void testMoveClassFromJavaFileToDependentModule() throws Exception {
    performTest("java/common/moveClassFromJavaFileToDependentModule").assertSuccessful();
  }

  // classModifiers tests

  @Test
  public void testAddStatic() throws Exception {
    performTest("java/classModifiers/addStatic").assertFailure();
  }

  @Test
  public void testBecameSealed() throws Exception {
    performTest("java/classModifiers/becameSealed").assertFailure();
  }

  @Test
  public void testChangeInnerClassModifiers() throws Exception {
    performTest("java/classModifiers/changeInnerClassModifiers").assertSuccessful();
  }

  @Test
  public void testDecAccess() throws Exception {
    performTest("java/classModifiers/decAccess").assertFailure();
  }

  @Test
  public void testDropAbstract() throws Exception {
    performTest("java/classModifiers/dropAbstract").assertSuccessful();
  }

  @Test
  public void testRemoveStatic() throws Exception {
    performTest("java/classModifiers/removeStatic").assertFailure();
  }

  @Test
  public void testSetAbstract() throws Exception {
    performTest("java/classModifiers/setAbstract").assertFailure();
  }

  @Test
  public void testSetFinal() throws Exception {
    performTest("java/classModifiers/setFinal").assertFailure();
  }

  @Test
  public void testSetFinal1() throws Exception {
    performTest("java/classModifiers/setFinal1").assertFailure();
  }

  // fieldModifiers tests

  @Test
  public void testSetFinalField() throws Exception {
    performTest("java/fieldModifiers/setFinal").assertFailure();
  }

  @Test
  public void testHidePackagePrivateWithPackagePrivate() throws Exception {
    performTest("java/fieldModifiers/hidePackagePrivateWithPackagePrivate").assertSuccessful();
  }

  @Test
  public void testHidePackagePrivateWithProtected() throws Exception {
    performTest("java/fieldModifiers/hidePackagePrivateWithProtected").assertSuccessful();
  }

  @Test
  public void testHidePackagePrivateWithPublic() throws Exception {
    performTest("java/fieldModifiers/hidePackagePrivateWithPublic").assertSuccessful();
  }

  @Test
  public void testHideProtectedWithPackagePrivate() throws Exception {
    performTest("java/fieldModifiers/hideProtectedWithPackagePrivate").assertFailure();
  }

  @Test
  public void testHideProtectedWithProtected() throws Exception {
    performTest("java/fieldModifiers/hideProtectedWithProtected").assertSuccessful();
  }

  @Test
  public void testHideProtectedWithPublic() throws Exception {
    performTest("java/fieldModifiers/hideProtectedWithPublic").assertSuccessful();
  }

  @Test
  public void testHidePublicWithPackagePrivate() throws Exception {
    performTest("java/fieldModifiers/hidePublicWithPackagePrivate").assertFailure();
  }

  @Test
  public void testHidePublicWithProtected() throws Exception {
    performTest("java/fieldModifiers/hidePublicWithProtected").assertFailure();
  }

  @Test
  public void testHidePublicWithPublic() throws Exception {
    performTest("java/fieldModifiers/hidePublicWithPublic").assertSuccessful();
  }

  @Test
  public void testSetPackagePrivate() throws Exception {
    performTest("java/fieldModifiers/setPackagePrivate").assertFailure();
  }

  @Test
  public void testSetPrivate() throws Exception {
    performTest("java/fieldModifiers/setPrivate").assertFailure();
  }

  @Test
  public void testSetPrivateToConstantField() throws Exception {
    performTest("java/fieldModifiers/setPrivateToConstantField").assertFailure();
  }

  @Test
  public void testSetProtected() throws Exception {
    performTest("java/fieldModifiers/setProtected").assertFailure();
  }

  @Test
  public void testSetStatic() throws Exception {
    performTest("java/fieldModifiers/setStatic").assertSuccessful();
  }

  @Test
  public void testUnsetStatic() throws Exception {
    performTest("java/fieldModifiers/unsetStatic").assertFailure();
  }

  @Test
  public void testUnsetStaticFinal() throws Exception {
    performTest("java/fieldModifiers/unsetStaticFinal").assertSuccessful();
  }

  // methodModifiers tests

  @Test
  public void testMethodChangePackagePrivateToProtected() throws Exception {
    performTest("java/methodModifiers/changePackagePrivateToProtected").assertFailure();
  }

  @Test
  public void testMethodChangePackagePrivateToPublic() throws Exception {
    performTest("java/methodModifiers/changePackagePrivateToPublic").assertFailure();
  }

  @Test
  public void testMethodChangePrivateToPackagePrivate() throws Exception {
    performTest("java/methodModifiers/changePrivateToPackagePrivate").assertFailure();
  }

  @Test
  public void testMethodChangePrivateToProtected() throws Exception {
    performTest("java/methodModifiers/changePrivateToProtected").assertFailure();
  }

  @Test
  public void testMethodChangePrivateToPublic() throws Exception {
    performTest("java/methodModifiers/changePrivateToPublic").assertFailure();
  }

  @Test
  public void testMethodChangeProtectedToPublic() throws Exception {
    performTest("java/methodModifiers/changeProtectedToPublic").assertFailure();
  }

  @Test
  public void testMethodDecConstructorAccess() throws Exception {
    performTest("java/methodModifiers/decConstructorAccess").assertFailure();
  }

  @Test
  public void testMethodIncAccess() throws Exception {
    performTest("java/methodModifiers/incAccess").assertFailure();
  }

  @Test
  public void testMethodSetAbstract() throws Exception {
    performTest("java/methodModifiers/setAbstract").assertFailure();
  }

  @Test
  public void testMethodSetFinal() throws Exception {
    performTest("java/methodModifiers/setFinal").assertFailure();
  }

  @Test
  public void testMethodSetPrivate() throws Exception {
    performTest("java/methodModifiers/setPrivate").assertFailure();
  }

  @Test
  public void testMethodSetProtected() throws Exception {
    performTest("java/methodModifiers/setProtected").assertSuccessful();
  }

  @Test
  public void testMethodSetProtectedFromPublic() throws Exception {
    performTest("java/methodModifiers/setProtectedFromPublic").assertFailure();
  }

  @Test
  public void testMethodSetStatic() throws Exception {
    performTest("java/methodModifiers/setStatic").assertFailure();
  }

  @Test
  public void testMethodUnsetAbstractForSAMInterface() throws Exception {
    performTest("java/methodModifiers/unsetAbstractForSAMInterface").assertFailure();
  }

  @Test
  public void testMethodUnsetFinal() throws Exception {
    performTest("java/methodModifiers/unsetFinal").assertSuccessful();
  }

  @Test
  public void testMethodUnsetStatic() throws Exception {
    performTest("java/methodModifiers/unsetStatic").assertFailure();
  }

  // fieldProperties tests

  @Test
  public void testConstantChain() throws Exception {
    performTest("java/fieldProperties/constantChain").assertSuccessful();
  }

  @Test
  public void testConstantChain1() throws Exception {
    performTest("java/fieldProperties/constantChain1").assertSuccessful();
  }

  @Test
  public void testConstantChain2() throws Exception {
    performTest("java/fieldProperties/constantChain2").assertSuccessful();
  }

  @Test
  public void testConstantChain3() throws Exception {
    performTest("java/fieldProperties/constantChain3").assertSuccessful();
  }

  @Test
  public void testConstantChainMultiModule() throws Exception {
    performTest("java/fieldProperties/constantChainMultiModule").assertSuccessful();
  }

  @Test
  public void testConstantRemove() throws Exception {
    performTest("java/fieldProperties/constantRemove").assertFailure();
  }

  @Test
  public void testConstantRemove1() throws Exception {
    performTest("java/fieldProperties/constantRemove1").assertFailure();
  }

  @Test
  public void testDoubleConstantChange() throws Exception {
    performTest("java/fieldProperties/doubleConstantChange").assertSuccessful();
  }

  @Test
  public void testFloatConstantChange() throws Exception {
    performTest("java/fieldProperties/floatConstantChange").assertSuccessful();
  }

  @Test
  public void testInnerConstantChange() throws Exception {
    performTest("java/fieldProperties/innerConstantChange").assertSuccessful();
  }

  @Test
  public void testIntConstantChange() throws Exception {
    performTest("java/fieldProperties/intConstantChange").assertSuccessful();
  }

  @Test
  public void testIntNonStaticConstantChange() throws Exception {
    performTest("java/fieldProperties/intNonStaticConstantChange").assertSuccessful();
  }

  @Test
  public void testLongConstantChange() throws Exception {
    performTest("java/fieldProperties/longConstantChange").assertSuccessful();
  }

  @Test
  public void testMutualConstants() throws Exception {
    performTest("java/fieldProperties/mutualConstants").assertSuccessful();
  }

  @Test
  public void testNonCompileTimeConstant() throws Exception {
    performTest("java/fieldProperties/nonCompileTimeConstant").assertSuccessful();
  }

  @Test
  public void testNonIncremental1() throws Exception {
    performTest("java/fieldProperties/nonIncremental1").assertSuccessful();
  }

  @Test
  public void testNonIncremental2() throws Exception {
    performTest("java/fieldProperties/nonIncremental2").assertSuccessful();
  }

  @Test
  public void testNonIncremental3() throws Exception {
    performTest("java/fieldProperties/nonIncremental3").assertSuccessful();
  }

  @Test
  public void testNonIncremental4() throws Exception {
    performTest("java/fieldProperties/nonIncremental4").assertSuccessful();
  }

  @Test
  public void testStringConstantChange() throws Exception {
    performTest("java/fieldProperties/stringConstantChange").assertSuccessful();
  }

  @Test
  public void testStringConstantChangeWithECJ() throws Exception {
    performTest("java/fieldProperties/stringConstantChangeWithECJ").assertSuccessful();
  }

  @Test
  public void testStringConstantLessAccessible() throws Exception {
    performTest("java/fieldProperties/stringConstantLessAccessible").assertFailure();
  }

  @Test
  public void testTypeChange() throws Exception {
    performTest("java/fieldProperties/typeChange").assertFailure();
  }

  @Test
  public void testTypeChange1() throws Exception {
    performTest("java/fieldProperties/typeChange1").assertFailure();
  }

  @Test
  public void testTypeChange2() throws Exception {
    performTest("java/fieldProperties/typeChange2").assertSuccessful();
  }

  // methodProperties tests

  @Test
  public void testAddThrows() throws Exception {
    performTest("java/methodProperties/addThrows").assertFailure();
  }

  @Test
  public void testChangeLambdaSAMMethodSignature() throws Exception {
    performTest("java/methodProperties/changeLambdaSAMMethodSignature").assertFailure();
  }

  @Test
  public void testChangeLambdaTargetReturnType() throws Exception {
    performTest("java/methodProperties/changeLambdaTargetReturnType").assertSuccessful();
  }

  @Test
  public void testChangeMethodRefReturnType() throws Exception {
    performTest("java/methodProperties/changeMethodRefReturnType").assertSuccessful();
  }

  @Test
  public void testChangeReturnType() throws Exception {
    performTest("java/methodProperties/changeReturnType").assertFailure();
  }

  @Test
  public void testChangeReturnType1() throws Exception {
    performTest("java/methodProperties/changeReturnType1").assertFailure();
  }

  @Test
  public void testChangeSAMMethodSignature() throws Exception {
    performTest("java/methodProperties/changeSAMMethodSignature").assertSuccessful();
  }

  @Test
  public void testChangeSAMMethodSignature2() throws Exception {
    performTest("java/methodProperties/changeSAMMethodSignature2").assertFailure();
  }

  @Test
  public void testChangeSignature() throws Exception {
    performTest("java/methodProperties/changeSignature").assertSuccessful();
  }

  @Test
  public void testChangeSignature1() throws Exception {
    performTest("java/methodProperties/changeSignature1").assertSuccessful();
  }

  // classProperties tests

  @Test
  public void testAddExtends() throws Exception {
    performTest("java/classProperties/addExtends").assertSuccessful();
  }

  @Test
  public void testAddImplements() throws Exception {
    performTest("java/classProperties/addImplements").assertSuccessful();
  }

  @Test
  public void testAddImplementsPatternMatching() throws Exception {
    performTest("java/classProperties/addImplementsPatternMatching").assertSuccessful();
  }

  @Test
  public void testChangeExtends() throws Exception {
    performTest("java/classProperties/changeExtends").assertSuccessful();
  }

  @Test
  public void testChangeExtends2() throws Exception {
    performTest("java/classProperties/changeExtends2").assertSuccessful();
  }

  @Test
  public void testConvertToCheckedException() throws Exception {
    performTest("java/classProperties/convertToCheckedException").assertFailure();
  }

  @Test
  public void testConvertToCheckedExceptionMultiModule() throws Exception {
    performTest("java/classProperties/convertToCheckedExceptionMultiModule").assertSuccessful();
  }

  @Test
  public void testRemoveExtends() throws Exception {
    performTest("java/classProperties/removeExtends").assertFailure();
  }

  @Test
  public void testRemoveExtendsAffectsFieldAccess() throws Exception {
    performTest("java/classProperties/removeExtendsAffectsFieldAccess").assertFailure();
  }

  @Test
  public void testRemoveExtendsAffectsMethodAccess() throws Exception {
    performTest("java/classProperties/removeExtendsAffectsMethodAccess").assertFailure();
  }

  @Test
  public void testRemoveImplements() throws Exception {
    performTest("java/classProperties/removeImplements").assertFailure();
  }

  @Test
  public void testRemoveImplements2() throws Exception {
    performTest("java/classProperties/removeImplements2").assertFailure();
  }

  @Test
  public void testRemoveImplements3() throws Exception {
    performTest("java/classProperties/removeImplements3").assertFailure();
  }

  // imports tests

  @Test
  public void testStaticImportConstantFieldChanged() throws Exception {
    performTest("java/imports/staticImportConstantFieldChanged").assertSuccessful();
  }

  @Test
  public void testUnusedClassImport() throws Exception {
    performTest("java/imports/unusedClassImport").assertFailure();
  }

  @Test
  public void testUnusedStaticImportClassDeleted() throws Exception {
    performTest("java/imports/unusedStaticImportClassDeleted").assertFailure();
  }

  @Test
  public void testUnusedStaticImportFieldBecameNonstatic() throws Exception {
    performTest("java/imports/unusedStaticImportFieldBecameNonstatic").assertFailure();
  }

  @Test
  public void testUnusedStaticImportFieldDeleted() throws Exception {
    performTest("java/imports/unusedStaticImportFieldDeleted").assertFailure();
  }

  @Test
  public void testUnusedStaticImportInheritedFieldBecameNonstatic() throws Exception {
    performTest("java/imports/unusedStaticImportInheritedFieldBecameNonstatic").assertFailure();
  }

  @Test
  public void testUnusedStaticImportInheritedFieldDeleted() throws Exception {
    performTest("java/imports/unusedStaticImportInheritedFieldDeleted").assertFailure();
  }

  @Test
  public void testUnusedStaticImportInheritedMethodBecameNonstatic() throws Exception {
    performTest("java/imports/unusedStaticImportInheritedMethodBecameNonstatic").assertFailure();
  }

  @Test
  public void testUnusedStaticImportInheritedMethodDeleted() throws Exception {
    performTest("java/imports/unusedStaticImportInheritedMethodDeleted").assertFailure();
  }

  @Test
  public void testUnusedStaticImportMethodBecameNonstatic() throws Exception {
    performTest("java/imports/unusedStaticImportMethodBecameNonstatic").assertFailure();
  }

  @Test
  public void testUnusedStaticImportMethodDeleted() throws Exception {
    performTest("java/imports/unusedStaticImportMethodDeleted").assertFailure();
  }

  @Test
  public void testUnusedStaticWildcardImport() throws Exception {
    performTest("java/imports/unusedStaticWildcardImport").assertFailure();
  }

  @Test
  public void testWildcardStaticImportFieldAdded() throws Exception {
    performTest("java/imports/wildcardStaticImportFieldAdded").assertFailure();
  }

  @Test
  public void testWildcardStaticImportFieldBecameStatic() throws Exception {
    performTest("java/imports/wildcardStaticImportFieldBecameStatic").assertFailure();
  }

  @Test
  public void testWildcardStaticImportMethodAdded() throws Exception {
    performTest("java/imports/wildcardStaticImportMethodAdded").assertFailure();
  }

  @Test
  public void testWildcardStaticImportMethodBecameStatic() throws Exception {
    performTest("java/imports/wildcardStaticImportMethodBecameStatic").assertFailure();
  }

  // annotations tests

  @Test
  public void testAnnotationAddAnnotationTarget() throws Exception {
    performTest("java/annotations/addAnnotationTarget").assertSuccessful();
  }

  @Test
  public void testAnnotationRemoveAnnotationTarget() throws Exception {
    performTest("java/annotations/removeAnnotationTarget").assertFailure();
  }

  @Test
  public void testAnnotationAddAnnotationTypeMemberWithDefaultValue() throws Exception {
    performTest("java/annotations/addAnnotationTypeMemberWithDefaultValue").assertSuccessful();
  }

  @Test
  public void testAnnotationAddAnnotationTypeMemberWithDefaultValue2() throws Exception {
    performTest("java/annotations/addAnnotationTypeMemberWithDefaultValue2").assertSuccessful();
  }

  @Test
  public void testAnnotationAddAnnotationTypeMemberWithoutDefaultValue() throws Exception {
    performTest("java/annotations/addAnnotationTypeMemberWithoutDefaultValue").assertFailure();
  }

  @Test
  public void testAnnotationAddDefaultToAnnotationMember() throws Exception {
    performTest("java/annotations/addDefaultToAnnotationMember").assertSuccessful();
  }

  @Test
  public void testAnnotationRemoveDefaultFromAnnotationMember() throws Exception {
    performTest("java/annotations/removeDefaultFromAnnotationMember").assertFailure();
  }

  @Test
  public void testAnnotationRemoveAnnotationTypeMember() throws Exception {
    performTest("java/annotations/removeAnnotationTypeMember").assertFailure();
  }

  @Test
  public void testAnnotationRemoveAnnotationTypeMember1() throws Exception {
    performTest("java/annotations/removeAnnotationTypeMember1").assertFailure();
  }

  @Test
  public void testAnnotationChangeAnnotationTypeMemberType() throws Exception {
    performTest("java/annotations/changeAnnotationTypeMemberType").assertFailure();
  }

  @Test
  public void testAnnotationChangeAnnotationTypeMemberTypeArray() throws Exception {
    performTest("java/annotations/changeAnnotationTypeMemberTypeArray").assertFailure();
  }

  @Test
  public void testAnnotationChangeAnnotationTypeMemberTypeEnumArray() throws Exception {
    performTest("java/annotations/changeAnnotationTypeMemberTypeEnumArray").assertFailure();
  }

  @Test
  public void testAnnotationChangeAnnotationRetentionPolicy() throws Exception {
    performTest("java/annotations/changeAnnotationRetentionPolicy").assertSuccessful();
  }

  @Test
  public void testAnnotationChangeAnnotationRetentionPolicy1() throws Exception {
    performTest("java/annotations/changeAnnotationRetentionPolicy1").assertSuccessful();
  }

  @Test
  public void testAnnotationChangeAnnotationRetentionPolicy2() throws Exception {
    performTest("java/annotations/changeAnnotationRetentionPolicy2").assertSuccessful();
  }

  @Test
  public void testAnnotationChangeAnnotationRetentionPolicy3() throws Exception {
    performTest("java/annotations/changeAnnotationRetentionPolicy3").assertSuccessful();
  }

  @Test
  public void testAnnotationChangeAnnotationRetentionPolicy4() throws Exception {
    performTest("java/annotations/changeAnnotationRetentionPolicy4").assertSuccessful();
  }

  @Test
  public void testAnnotationAddAnnotationTargetTypeUse() throws Exception {
    performTest("java/annotations/addAnnotationTargetTypeUse").assertSuccessful();
  }

  @Test
  public void testAnnotationAddTypeUseAnnotationTarget() throws Exception {
    performTest("java/annotations/addTypeUseAnnotationTarget").assertSuccessful();
  }

  @Test
  public void testAnnotationRemoveTypeUseAnnotationTarget() throws Exception {
    performTest("java/annotations/removeTypeUseAnnotationTarget").assertSuccessful();
  }

  @Test
  public void testAnnotationAddRecordComponentAnnotationTarget() throws Exception {
    performTest("java/annotations/addRecordComponentAnnotationTarget").assertSuccessful();
  }

  @Test
  public void testAnnotationClassAsArgument() throws Exception {
    performTest("java/annotations/classAsArgument").assertFailure();
  }

  /*
  Discussion is needed --- unnecessarily conservative
  @Test
  public void testAnnotationMetaAnnotationChanged() throws Exception {
    performTest("java/annotations/metaAnnotationChanged").assertSuccessful();
  }

  @Test
  public void testAnnotationMetaAnnotationChangedCascade() throws Exception {
    performTest("java/annotations/metaAnnotationChangedCascade").assertSuccessful();
  }

  @Test
  public void testAnnotationMetaAnnotationChangedCascade2() throws Exception {
    performTest("java/annotations/metaAnnotationChangedCascade2").assertSuccessful();
  }
  */

  @Test
  public void testAnnotationsTracker() throws Exception {
    performTest("java/annotations/annotationsTracker").assertSuccessful();
  }

  // generics tests

  @Test
  public void testGenericsAddMethodToBase() throws Exception {
    performTest("java/generics/addMethodToBase").assertFailure();
  }

  @Test
  public void testGenericsAddParameterizedMethodToBase() throws Exception {
    performTest("java/generics/addParameterizedMethodToBase").assertFailure();
  }

  @Test
  public void testGenericsArgumentContainment() throws Exception {
    performTest("java/generics/argumentContainment").assertFailure();
  }

  @Test
  public void testGenericsArgumentContainment2() throws Exception {
    performTest("java/generics/argumentContainment2").assertFailure();
  }

  @Test
  public void testGenericsArgumentContainment3() throws Exception {
    performTest("java/generics/argumentContainment3").assertFailure();
  }

  @Test
  public void testGenericsChangeBound() throws Exception {
    performTest("java/generics/changeBound").assertFailure();
  }

  @Test
  public void testGenericsChangeBound1() throws Exception {
    performTest("java/generics/changeBound1").assertFailure();
  }

  @Test
  public void testGenericsChangeBoundClass1() throws Exception {
    performTest("java/generics/changeBoundClass1").assertFailure();
  }

  @Test
  public void testGenericsChangeBoundedClass() throws Exception {
    performTest("java/generics/changeBoundedClass").assertFailure();
  }

  @Test
  public void testGenericsChangeBoundInterface1() throws Exception {
    performTest("java/generics/changeBoundInterface1").assertFailure();
  }

  @Test
  public void testGenericsChangeExtends() throws Exception {
    performTest("java/generics/changeExtends").assertFailure();
  }

  @Test
  public void testGenericsChangeExtends1() throws Exception {
    performTest("java/generics/changeExtends1").assertSuccessful();
  }

  @Test
  public void testGenericsChangeExtends2() throws Exception {
    performTest("java/generics/changeExtends2").assertFailure();
  }

  @Test
  public void testGenericsChangeImplements() throws Exception {
    performTest("java/generics/changeImplements").assertSuccessful();
  }

  @Test
  public void testGenericsChangeInterfaceTypeParameter() throws Exception {
    performTest("java/generics/changeInterfaceTypeParameter").assertFailure();
  }

  @Test
  public void testGenericsChangeToCovariantMethodInBase() throws Exception {
    performTest("java/generics/changeToCovariantMethodInBase").assertSuccessful();
  }

  @Test
  public void testGenericsChangeToCovariantMethodInBase2() throws Exception {
    performTest("java/generics/changeToCovariantMethodInBase2").assertSuccessful();
  }

  @Test
  public void testGenericsChangeToCovariantMethodInBase3() throws Exception {
    performTest("java/generics/changeToCovariantMethodInBase3").assertSuccessful();
  }

  @Test
  public void testGenericsChangeToCovariantMethodInBase4() throws Exception {
    performTest("java/generics/changeToCovariantMethodInBase4").assertSuccessful();
  }

  @Test
  public void testGenericsChangeVarargSignature() throws Exception {
    performTest("java/generics/changeVarargSignature").assertFailure();
  }

  @Test
  public void testGenericsChangeVarargSignature1() throws Exception {
    performTest("java/generics/changeVarargSignature1").assertFailure();
  }

  @Test
  public void testGenericsCovariance() throws Exception {
    performTest("java/generics/covariance").assertSuccessful();
  }

  @Test
  public void testGenericsCovariance1() throws Exception {
    performTest("java/generics/covariance1").assertSuccessful();
  }

  @Test
  public void testGenericsCovariance2() throws Exception {
    performTest("java/generics/covariance2").assertFailure();
  }

  @Test
  public void testGenericsCovarianceNoChanges() throws Exception {
    performTest("java/generics/covarianceNoChanges").assertSuccessful();
  }

  @Test
  public void testGenericsDegenerify() throws Exception {
    performTest("java/generics/degenerify").assertFailure();
  }

  @Test
  public void testGenericsDegenerify1() throws Exception {
    performTest("java/generics/degenerify1").assertFailure();
  }

  @Test
  public void testGenericsFieldTypeChange() throws Exception {
    performTest("java/generics/fieldTypeChange").assertFailure();
  }

  @Test
  public void testGenericsImplicitOverrideMethodSignatureChanged() throws Exception {
    performTest("java/generics/implicitOverrideMethodSignatureChanged").assertFailure();
  }

  @Test
  public void testGenericsOverrideAnnotatedAnonymous() throws Exception {
    performTest("java/generics/overrideAnnotatedAnonymous").assertFailure();
  }

  @Test
  public void testGenericsOverrideAnnotatedAnonymousNotRecompile() throws Exception {
    performTest("java/generics/overrideAnnotatedAnonymousNotRecompile").assertSuccessful();
  }

  @Test
  public void testGenericsOverrideAnnotatedInner() throws Exception {
    performTest("java/generics/overrideAnnotatedInner").assertFailure();
  }

  @Test
  public void testGenericsParamTypes() throws Exception {
    performTest("java/generics/paramTypes").assertFailure();
  }

  @Test
  public void testGenericsReturnType() throws Exception {
    performTest("java/generics/returnType").assertFailure();
  }

  // membersChange tests

  @Test
  public void testMembersAddAbstractMethod() throws Exception {
    performTest("java/membersChange/addAbstractMethod").assertFailure();
  }

  @Test
  public void testMembersAddConstructorParameter() throws Exception {
    performTest("java/membersChange/addConstructorParameter").assertFailure();
  }

  @Test
  public void testMembersAddFieldOfSameKindToBaseClass() throws Exception {
    performTest("java/membersChange/addFieldOfSameKindToBaseClass").assertSuccessful();
  }

  @Test
  public void testMembersAddFieldToBaseClass() throws Exception {
    performTest("java/membersChange/addFieldToBaseClass").assertSuccessful();
  }

  @Test
  public void testMembersAddFieldToDerived() throws Exception {
    performTest("java/membersChange/addFieldToDerived").assertFailure();
  }

  @Test
  public void testMembersAddFieldToEnum() throws Exception {
    performTest("java/membersChange/addFieldToEnum").assertFailure();
  }

  @Test
  public void testMembersAddFieldToInterface() throws Exception {
    performTest("java/membersChange/addFieldToInterface").assertFailure();
  }

  @Test
  public void testMembersAddFieldToInterface2() throws Exception {
    performTest("java/membersChange/addFieldToInterface2").assertFailure();
  }

  @Test
  public void testMembersAddFinalMethodHavingNonFinalMethodInSubclass() throws Exception {
    performTest("java/membersChange/addFinalMethodHavingNonFinalMethodInSubclass").assertFailure();
  }

  @Test
  public void testMembersAddHidingField() throws Exception {
    performTest("java/membersChange/addHidingField").assertSuccessful();
  }

  @Test
  public void testMembersAddHidingMethod() throws Exception {
    performTest("java/membersChange/addHidingMethod").assertSuccessful();
  }

  @Test
  public void testMembersAddInterfaceMethod() throws Exception {
    performTest("java/membersChange/addInterfaceMethod").assertFailure();
  }

  @Test
  public void testMembersAddInterfaceMethod2() throws Exception {
    performTest("java/membersChange/addInterfaceMethod2").assertFailure();
  }

  @Test
  public void testMembersAddLambdaTargetMethod() throws Exception {
    performTest("java/membersChange/addLambdaTargetMethod").assertFailure();
  }

  @Test
  public void testMembersAddLambdaTargetMethod2() throws Exception {
    performTest("java/membersChange/addLambdaTargetMethod2").assertSuccessful();
  }

  @Test
  public void testMembersAddLambdaTargetMethodNoRecompile() throws Exception {
    performTest("java/membersChange/addLambdaTargetMethodNoRecompile").assertSuccessful();
  }

  @Test
  public void testMembersAddLessAccessibleFieldToDerived() throws Exception {
    performTest("java/membersChange/addLessAccessibleFieldToDerived").assertFailure();
  }

  @Test
  public void testMembersAddMethod() throws Exception {
    performTest("java/membersChange/addMethod").assertSuccessful();
  }

  @Test
  public void testMembersAddMethodWithCovariantReturnType() throws Exception {
    performTest("java/membersChange/addMethodWithCovariantReturnType").assertSuccessful();
  }

  @Test
  public void testMembersAddMethodWithIncompatibleReturnType() throws Exception {
    performTest("java/membersChange/addMethodWithIncompatibleReturnType").assertFailure();
  }

  @Test
  public void testMembersAddMoreAccessibleMethodToBase() throws Exception {
    performTest("java/membersChange/addMoreAccessibleMethodToBase").assertFailure();
  }

  @Test
  public void testMembersAddMoreSpecific() throws Exception {
    performTest("java/membersChange/addMoreSpecific").assertSuccessful();
  }

  @Test
  public void testMembersAddMoreSpecific1() throws Exception {
    performTest("java/membersChange/addMoreSpecific1").assertSuccessful();
  }

  @Test
  public void testMembersAddMoreSpecific2() throws Exception {
    performTest("java/membersChange/addMoreSpecific2").assertSuccessful();
  }

  @Test
  public void testMembersAddNonStaticMethodHavingStaticMethodInSubclass() throws Exception {
    performTest("java/membersChange/addNonStaticMethodHavingStaticMethodInSubclass").assertFailure();
  }

  @Test
  public void testMembersAddOverloadingConstructor() throws Exception {
    performTest("java/membersChange/addOverloadingConstructor").assertFailure();
  }

  @Test
  public void testMembersAddOverloadingMethod() throws Exception {
    performTest("java/membersChange/addOverloadingMethod").assertFailure();
  }

  @Test
  public void testMembersAddOverridingMethodAndChangeReturnType() throws Exception {
    performTest("java/membersChange/addOverridingMethodAndChangeReturnType").assertSuccessful();
  }

  @Test
  public void testMembersAddPackagePrivateMethodToParentClashingWithMethodFromInterface() throws Exception {
    performTest("java/membersChange/addPackagePrivateMethodToParentClashingWithMethodFromInterface").assertFailure();
  }

  @Test
  public void testMembersAddParameterToConstructor() throws Exception {
    performTest("java/membersChange/addParameterToConstructor").assertFailure();
  }

  @Test
  public void testMembersAddPrivateMethodToAbstractClass() throws Exception {
    performTest("java/membersChange/addPrivateMethodToAbstractClass").assertSuccessful();
  }

  @Test
  public void testMembersAddSAMInterfaceAbstractMethod() throws Exception {
    performTest("java/membersChange/addSAMInterfaceAbstractMethod").assertFailure();
  }

  @Test
  public void testMembersAddSAMInterfaceMethod() throws Exception {
    performTest("java/membersChange/addSAMInterfaceMethod").assertSuccessful();
  }

  @Test
  public void testMembersAddStaticFieldToDerived() throws Exception {
    performTest("java/membersChange/addStaticFieldToDerived").assertSuccessful();
  }

  @Test
  public void testMembersAddVarargMethod() throws Exception {
    performTest("java/membersChange/addVarargMethod").assertFailure();
  }

  @Test
  public void testMembersChangeMethodGenericReturnType() throws Exception {
    performTest("java/membersChange/changeMethodGenericReturnType").assertFailure();
  }

  @Test
  public void testMembersChangeSAMInterfaceMethodToAbstract() throws Exception {
    performTest("java/membersChange/changeSAMInterfaceMethodToAbstract").assertFailure();
  }

  @Test
  public void testMembersChangeStaticMethodSignature() throws Exception {
    performTest("java/membersChange/changeStaticMethodSignature").assertFailure();
  }

  @Test
  public void testMembersDeleteConstructor() throws Exception {
    performTest("java/membersChange/deleteConstructor").assertFailure();
  }

  @Test
  public void testMembersDeleteInner() throws Exception {
    performTest("java/membersChange/deleteInner").assertFailure();
  }

  @Test
  public void testMembersDeleteInterfaceMethod() throws Exception {
    performTest("java/membersChange/deleteInterfaceMethod").assertFailure();
  }

  @Test
  public void testMembersDeleteMethod() throws Exception {
    performTest("java/membersChange/deleteMethod").assertFailure();
  }

  @Test
  public void testMembersDeleteMethodImplementation() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation").assertFailure();
  }

  @Test
  public void testMembersDeleteMethodImplementation2() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation2").assertSuccessful();
  }

  @Test
  public void testMembersDeleteMethodImplementation3() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation3").assertFailure();
  }

  @Test
  public void testMembersDeleteMethodImplementation4() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation4").assertFailure();
  }

  @Test
  public void testMembersDeleteMethodImplementation5() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation5").assertSuccessful();
  }

  @Test
  public void testMembersDeleteMethodImplementation6() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation6").assertSuccessful();
  }

  @Test
  public void testMembersDeleteMethodImplementation7() throws Exception {
    performTest("java/membersChange/deleteMethodImplementation7").assertFailure();
  }

  @Test
  public void testMembersDeleteOverridingPackageLocalMethodImpl() throws Exception {
    performTest("java/membersChange/deleteOverridingPackageLocalMethodImpl").assertFailure();
  }

  @Test
  public void testMembersDeleteOverridingPackageLocalMethodImpl2() throws Exception {
    performTest("java/membersChange/deleteOverridingPackageLocalMethodImpl2").assertFailure();
  }

  @Test
  public void testMembersDeleteSAMInterfaceMethod() throws Exception {
    performTest("java/membersChange/deleteSAMInterfaceMethod").assertFailure();
  }

  @Test
  public void testMembersHierarchy() throws Exception {
    performTest("java/membersChange/hierarchy").assertFailure();
  }

  @Test
  public void testMembersHierarchy2() throws Exception {
    performTest("java/membersChange/hierarchy2").assertFailure();
  }

  @Test
  public void testMembersMoveMethodToSubclass() throws Exception {
    performTest("java/membersChange/moveMethodToSubclass").assertSuccessful();
  }

  @Test
  public void testMembersPushFieldDown() throws Exception {
    performTest("java/membersChange/pushFieldDown").assertSuccessful();
  }

  @Test
  public void testMembersRemoveBaseImplementation() throws Exception {
    performTest("java/membersChange/removeBaseImplementation").assertFailure();
  }

  @Test
  public void testMembersRemoveHidingField() throws Exception {
    performTest("java/membersChange/removeHidingField").assertSuccessful();
  }

  @Test
  public void testMembersRemoveHidingMethod() throws Exception {
    performTest("java/membersChange/removeHidingMethod").assertFailure();
  }

  @Test
  public void testMembersRemoveMoreAccessibleMethod() throws Exception {
    performTest("java/membersChange/removeMoreAccessibleMethod").assertFailure();
  }

  @Test
  public void testMembersRemoveThrowsInBaseMethod() throws Exception {
    performTest("java/membersChange/removeThrowsInBaseMethod").assertFailure();
  }

  @Test
  public void testMembersRenameMethod() throws Exception {
    performTest("java/membersChange/renameMethod").assertFailure();
  }

  @Test
  public void testMembersRenameSAMInterfaceMethod() throws Exception {
    performTest("java/membersChange/renameSAMInterfaceMethod").assertSuccessful();
  }

  @Test
  public void testMembersReplaceMethodWithBridge() throws Exception {
    performTest("java/membersChange/replaceMethodWithBridge").assertFailure();
  }

  @Test
  public void testMembersThrowsListDiffersInBaseAndDerived() throws Exception {
    performTest("java/membersChange/throwsListDiffersInBaseAndDerived").assertFailure();
  }

  // markDirty tests

  @Test
  public void testRecompileDependent() throws Exception {
    performTest("java/markDirty/recompileDependent").assertSuccessful();
  }

  @Test
  public void testRecompileTwinDependencies() throws Exception {
    performTest("java/markDirty/recompileTwinDependencies").assertSuccessful();
  }

  // packageInfo tests

  @Test
  public void testPackageInfoRecompileOnConstantChange() throws Exception {
    performTest("java/packageInfo/packageInfoRecompileOnConstantChange").assertSuccessful();
  }

  // changeName tests

  @Test
  public void testChangeCaseOfName() throws Exception {
    performTest("java/changeName/changeCaseOfName").assertSuccessful();
  }

  @Test
  public void testChangeClassName() throws Exception {
    performTest("java/changeName/changeClassName").assertSuccessful();
  }

  // renameModule tests

  @Test
  public void testDeleteClassSameModule() throws Exception {
    performTest("java/renameModule/deleteClassSameModule").assertSuccessful();
  }

  @Test
  public void testDeleteClassDependentModule() throws Exception {
    performTest("java/renameModule/deleteClassDependentModule").assertSuccessful();
  }

  // java9-features tests

  @Test
  public void testChangeQualifiedTransitiveModuleExportsNoRebuild() throws Exception {
    performTest("java/java9-features/changeQualifiedTransitiveModuleExportsNoRebuild").assertSuccessful();
  }

  @Test
  public void testChangeQualifiedTransitiveModuleExportsRebuildDirectDeps() throws Exception {
    performTest("java/java9-features/changeQualifiedTransitiveModuleExportsRebuildDirectDeps").assertSuccessful();
  }

  @Test
  public void testChangeQualifiedTransitiveModuleExportsRebuildIndirectDeps() throws Exception {
    performTest("java/java9-features/changeQualifiedTransitiveModuleExportsRebuildIndirectDeps").assertFailure();
  }

  @Test
  public void testChangeQualifiedTransitiveModuleRequires() throws Exception {
    performTest("java/java9-features/changeQualifiedTransitiveModuleRequires").assertSuccessful();
  }

  @Test
  public void testChangeTransitiveModuleRequires() throws Exception {
    performTest("java/java9-features/changeTransitiveModuleRequires").assertFailure();
  }

  @Test
  public void testModuleInfoAdded() throws Exception {
    performTest("java/java9-features/moduleInfoAdded").assertSuccessful();
  }

  @Test
  public void testRemoveModuleExports() throws Exception {
    performTest("java/java9-features/removeModuleExports").assertSuccessful();
  }

  @Test
  public void testRemoveModuleRequires() throws Exception {
    performTest("java/java9-features/removeModuleRequires").assertFailure();
  }

  @Test
  public void testRemoveQualifiedModuleExports() throws Exception {
    performTest("java/java9-features/removeQualifiedModuleExports").assertSuccessful();
  }

  @Test
  public void testRemoveQualifiedTransitiveModuleExports() throws Exception {
    performTest("java/java9-features/removeQualifiedTransitiveModuleExports").assertFailure();
  }

  @Test
  public void testRemoveTransitiveModuleExports() throws Exception {
    performTest("java/java9-features/removeTransitiveModuleExports").assertFailure();
  }

  @Test
  public void testRemoveTransitiveModuleRequires() throws Exception {
    performTest("java/java9-features/removeTransitiveModuleRequires").assertFailure();
  }
}
