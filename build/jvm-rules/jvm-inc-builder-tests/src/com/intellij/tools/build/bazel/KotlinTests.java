package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.Test;

// Migrated from JPS-based test engine (pureKotlin tests)
public class KotlinTests extends BazelIncBuildTest {

  // pureKotlin tests

  @Test
  public void testAccessingFunctionsViaPackagePart() throws Exception {
    performTest("kotlin/pureKotlin/accessingFunctionsViaPackagePart").assertSuccessful();
  }

  @Test
  public void testAccessingPropertiesViaField() throws Exception {
    performTest("kotlin/pureKotlin/accessingPropertiesViaField").assertSuccessful();
  }

  @Test
  public void testAddClass() throws Exception {
    performTest("kotlin/pureKotlin/addClass").assertSuccessful();
  }

  @Test
  public void testAddFileWithFunctionOverload() throws Exception {
    performTest("kotlin/pureKotlin/addFileWithFunctionOverload").assertSuccessful();
  }

  @Test
  public void testAddMemberTypeAlias() throws Exception {
    performTest("kotlin/pureKotlin/addMemberTypeAlias").assertSuccessful();
  }

  @Test
  public void testAddTopLevelTypeAlias() throws Exception {
    performTest("kotlin/pureKotlin/addTopLevelTypeAlias").assertSuccessful();
  }

  @Test
  public void testAnnotations() throws Exception {
    performTest("kotlin/pureKotlin/annotations").assertSuccessful();
  }

  @Test
  public void testAnonymousObjectChanged() throws Exception {
    performTest("kotlin/pureKotlin/anonymousObjectChanged").assertSuccessful();
  }

  @Test
  public void testChangeTopLevelTypeAlias() throws Exception {
    performTest("kotlin/pureKotlin/changeTopLevelTypeAlias").assertSuccessful();
  }

  @Test
  public void testChangeTypealiasTypeWithHierarhy() throws Exception {
    performTest("kotlin/pureKotlin/changeTypealiasTypeWithHierarhy").assertSuccessful();
  }

  @Test
  public void testChangeTypeImplicitlyWithCircularDependency() throws Exception {
    performTest("kotlin/pureKotlin/changeTypeImplicitlyWithCircularDependency").assertSuccessful();
  }

  @Test
  public void testChangeTypeWithHierarhyDependency() throws Exception {
    performTest("kotlin/pureKotlin/changeTypeWithHierarhyDependency").assertSuccessful();
  }

  @Test
  public void testChangeWithRemovingUsage() throws Exception {
    performTest("kotlin/pureKotlin/changeWithRemovingUsage").assertSuccessful();
  }

  @Test
  public void testCheckConstants() throws Exception {
    performTest(5, "kotlin/pureKotlin/checkConstants").assertSuccessful();
  }

  @Test
  public void testClassInlineFunctionChanged() throws Exception {
    performTest("kotlin/pureKotlin/classInlineFunctionChanged").assertSuccessful();
  }

  @Test
  public void testClassObjectConstantChanged() throws Exception {
    performTest("kotlin/pureKotlin/classObjectConstantChanged").assertSuccessful();
  }

  @Test
  public void testClassRecreated() throws Exception {
    performTest(2, "kotlin/pureKotlin/classRecreated").assertSuccessful();
  }

  @Test
  public void testClassRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/classRemoved").assertSuccessful();
  }

  @Test
  public void testClassSignatureChanged() throws Exception {
    performTest("kotlin/pureKotlin/classSignatureChanged").assertSuccessful();
  }

  @Test
  public void testClassSignatureUnchanged() throws Exception {
    performTest("kotlin/pureKotlin/classSignatureUnchanged").assertSuccessful();
  }

  @Test
  public void testCompanionConstantChanged() throws Exception {
    performTest("kotlin/pureKotlin/companionConstantChanged").assertSuccessful();
  }

  @Test
  public void testCompilationErrorThenFixedOtherPackage() throws Exception {
    performTest(2, "kotlin/pureKotlin/compilationErrorThenFixedOtherPackage").assertSuccessful();
  }

  @Test
  public void testCompilationErrorThenFixedSamePackage() throws Exception {
    performTest(2, "kotlin/pureKotlin/compilationErrorThenFixedSamePackage").assertSuccessful();
  }

  @Test
  public void testCompilationErrorThenFixedWithPhantomPart() throws Exception {
    performTest(2, "kotlin/pureKotlin/compilationErrorThenFixedWithPhantomPart").assertSuccessful();
  }

  @Test
  public void testCompilationErrorThenFixedWithPhantomPart2() throws Exception {
    performTest(2, "kotlin/pureKotlin/compilationErrorThenFixedWithPhantomPart2").assertSuccessful();
  }

  @Test
  public void testCompilationErrorThenFixedWithPhantomPart3() throws Exception {
    performTest(2, "kotlin/pureKotlin/compilationErrorThenFixedWithPhantomPart3").assertSuccessful();
  }

  @Test
  public void testConstantRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/constantRemoved").assertSuccessful();
  }

  @Test
  public void testConstantsUnchanged() throws Exception {
    performTest("kotlin/pureKotlin/constantsUnchanged").assertSuccessful();
  }

  @Test
  public void testConstantValueChanged() throws Exception {
    performTest("kotlin/pureKotlin/constantValueChanged").assertSuccessful();
  }

  @Test
  public void testDefaultArgumentInConstructorAdded() throws Exception {
    performTest("kotlin/pureKotlin/defaultArgumentInConstructorAdded").assertSuccessful();
  }

  @Test
  public void testDefaultArgumentInConstructorRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/defaultArgumentInConstructorRemoved").assertSuccessful();
  }

  @Test
  public void testDefaultValueAdded() throws Exception {
    performTest("kotlin/pureKotlin/defaultValueAdded").assertSuccessful();
  }

  @Test
  public void testDefaultValueChanged() throws Exception {
    performTest("kotlin/pureKotlin/defaultValueChanged").assertSuccessful();
  }

  @Test
  public void testDefaultValueInConstructorChanged() throws Exception {
    performTest("kotlin/pureKotlin/defaultValueInConstructorChanged").assertSuccessful();
  }

  @Test
  public void testDefaultValueInConstructorRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/defaultValueInConstructorRemoved").assertSuccessful();
  }

  @Test
  public void testDefaultValueRemoved1() throws Exception {
    performTest(2, "kotlin/pureKotlin/defaultValueRemoved1").assertSuccessful();
  }

  @Test
  public void testDefaultValueRemoved2() throws Exception {
    performTest(2, "kotlin/pureKotlin/defaultValueRemoved2").assertSuccessful();
  }

  @Test
  public void testDelegatedPropertyInlineExtensionAccessor() throws Exception {
    performTest(2, "kotlin/pureKotlin/delegatedPropertyInlineExtensionAccessor").assertSuccessful();
  }

  @Test
  public void testDelegatedPropertyInlineMethodAccessor() throws Exception {
    performTest(2, "kotlin/pureKotlin/delegatedPropertyInlineMethodAccessor").assertSuccessful();
  }

  @Test
  public void testDependencyClassReferenced() throws Exception {
    performTest("kotlin/pureKotlin/dependencyClassReferenced").assertSuccessful();
  }

  @Test
  public void testDeprecateFunction() throws Exception {
    performTest("kotlin/pureKotlin/deprecateFunction").assertFailure();
  }

  @Test
  public void testDeprecateProperty() throws Exception {
    performTest("kotlin/pureKotlin/deprecateProperty").assertFailure();
  }

  @Test
  public void testDeprecatePropertyGetter() throws Exception {
    performTest("kotlin/pureKotlin/deprecatePropertyGetter").assertFailure();
  }

  @Test
  public void testDeprecatePropertySetter() throws Exception {
    performTest("kotlin/pureKotlin/deprecatePropertySetter").assertFailure();
  }

  @Test
  public void testEntriesMappings() throws Exception {
    performTest("kotlin/pureKotlin/entriesMappings").assertSuccessful();
  }

  @Test
  public void testFilesExchangePackages() throws Exception {
    performTest("kotlin/pureKotlin/filesExchangePackages").assertSuccessful();
  }

  @Test
  public void testFileWithConstantRemoved() throws Exception {
    performTest("kotlin/pureKotlin/fileWithConstantRemoved").assertFailure();
  }

  @Test
  public void testFileWithInlineFunctionRemoved() throws Exception {
    performTest("kotlin/pureKotlin/fileWithInlineFunctionRemoved").assertFailure();
  }

  @Test
  public void testFunctionBecameInline() throws Exception {
    performTest("kotlin/pureKotlin/functionBecameInline").assertSuccessful();
  }

  @Test
  public void testFunctionReferencingClass() throws Exception {
    performTest("kotlin/pureKotlin/functionReferencingClass").assertSuccessful();
  }

  @Test
  public void testFunRedeclaration() throws Exception {
    performTest("kotlin/pureKotlin/funRedeclaration").assertFailure();
  }

  @Test
  public void testFunVsConstructorOverloadConflict() throws Exception {
    performTest("kotlin/pureKotlin/funVsConstructorOverloadConflict").assertFailure();
  }

  @Test
  public void testGenericContextReceiver() throws Exception {
    performTest("kotlin/pureKotlin/genericContextReceiver").assertSuccessful();
  }

  @Test
  public void testIndependentClasses() throws Exception {
    performTest("kotlin/pureKotlin/independentClasses").assertSuccessful();
  }

  @Test
  public void testInlineFunctionBecomesNonInline() throws Exception {
    performTest("kotlin/pureKotlin/inlineFunctionBecomesNonInline").assertSuccessful();
  }

  @Test
  public void testInlineFunctionsCircularDependency() throws Exception {
    performTest("kotlin/pureKotlin/inlineFunctionsCircularDependency").assertSuccessful();
  }

  @Test
  public void testInlineFunctionsUnchanged() throws Exception {
    performTest("kotlin/pureKotlin/inlineFunctionsUnchanged").assertSuccessful();
  }

  @Test
  public void testInlineFunctionUsageAdded() throws Exception {
    performTest("kotlin/pureKotlin/inlineFunctionUsageAdded").assertSuccessful();
  }

  @Test
  public void testInlineLinesChanged() throws Exception {
    performTest("kotlin/pureKotlin/inlineLinesChanged").assertSuccessful();
  }

  @Test
  public void testInlineModifiedWithUsage() throws Exception {
    performTest("kotlin/pureKotlin/inlineModifiedWithUsage").assertSuccessful();
  }

  @Test
  public void testInlinePrivateFunctionAdded() throws Exception {
    performTest("kotlin/pureKotlin/inlinePrivateFunctionAdded").assertSuccessful();
  }

  @Test
  public void testInlinePropertyInClass() throws Exception {
    performTest(2, "kotlin/pureKotlin/inlinePropertyInClass").assertSuccessful();
  }

  @Test
  public void testInlinePropertyOnTopLevel() throws Exception {
    performTest(2, "kotlin/pureKotlin/inlinePropertyOnTopLevel").assertSuccessful();
  }

  @Test
  public void testInlineSuspendFunctionChanged() throws Exception {
    performTest("kotlin/pureKotlin/inlineSuspendFunctionChanged").assertSuccessful();
  }

  @Test
  public void testInlineTwoFunctionsOneChanged() throws Exception {
    performTest("kotlin/pureKotlin/inlineTwoFunctionsOneChanged").assertSuccessful();
  }

  @Test
  public void testInlineUsedWhereDeclared() throws Exception {
    performTest(2, "kotlin/pureKotlin/inlineUsedWhereDeclared").assertSuccessful();
  }

  @Test
  public void testInnerClassesFromSupertypes() throws Exception {
    performTest("kotlin/pureKotlin/innerClassesFromSupertypes").assertSuccessful();
  }

  @Test
  public void testInternalClassChanged() throws Exception {
    performTest("kotlin/pureKotlin/internalClassChanged").assertSuccessful();
  }

  @Test
  public void testInternalMemberInClassChanged() throws Exception {
    performTest("kotlin/pureKotlin/internalMemberInClassChanged").assertSuccessful();
  }

  @Test
  public void testInternalTypealias() throws Exception {
    performTest("kotlin/pureKotlin/internalTypealias").assertSuccessful();
  }

  @Test
  public void testInternalTypealiasConstructor() throws Exception {
    performTest("kotlin/pureKotlin/internalTypealiasConstructor").assertSuccessful();
  }

  @Test
  public void testInternalTypealiasObject() throws Exception {
    performTest("kotlin/pureKotlin/internalTypealiasObject").assertSuccessful();
  }

  @Test
  public void testLocalClassChanged() throws Exception {
    performTest("kotlin/pureKotlin/localClassChanged").assertSuccessful();
  }

  @Test
  public void testMoveClass() throws Exception {
    performTest(2, "kotlin/pureKotlin/moveClass").assertSuccessful();
  }

  @Test
  public void testMoveFileWithChangingPackage() throws Exception {
    performTest("kotlin/pureKotlin/moveFileWithChangingPackage").assertSuccessful();
  }

  @Test
  public void testMoveFileWithoutChangingPackage() throws Exception {
    performTest("kotlin/pureKotlin/moveFileWithoutChangingPackage").assertSuccessful();
  }

  @Test
  public void testMultiplePackagesModified() throws Exception {
    performTest("kotlin/pureKotlin/multiplePackagesModified").assertSuccessful();
  }

  @Test
  public void testObjectConstantChanged() throws Exception {
    performTest("kotlin/pureKotlin/objectConstantChanged").assertSuccessful();
  }

  @Test
  public void testOurClassReferenced() throws Exception {
    performTest(2, "kotlin/pureKotlin/ourClassReferenced").assertSuccessful();
  }

  @Test
  public void testOverloadInlined() throws Exception {
    performTest("kotlin/pureKotlin/overloadInlined").assertSuccessful();
  }

  @Test
  public void testPackageConstantChanged() throws Exception {
    performTest("kotlin/pureKotlin/packageConstantChanged").assertSuccessful();
  }

  @Test
  public void testPackageFileAdded() throws Exception {
    performTest("kotlin/pureKotlin/packageFileAdded").assertSuccessful();
  }

  @Test
  public void testPackageFileChangedPackage() throws Exception {
    performTest("kotlin/pureKotlin/packageFileChangedPackage").assertSuccessful();
  }

  @Test
  public void testPackageFileChangedThenOtherRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/packageFileChangedThenOtherRemoved").assertSuccessful();
  }

  @Test
  public void testPackageFileRemoved() throws Exception {
    performTest(2, "kotlin/pureKotlin/packageFileRemoved").assertSuccessful();
  }

  @Test
  public void testPackageFilesChangedInTurn() throws Exception {
    performTest(3, "kotlin/pureKotlin/packageFilesChangedInTurn").assertSuccessful();
  }

  @Test
  public void testPackageInlineFunctionAccessingField() throws Exception {
    performTest("kotlin/pureKotlin/packageInlineFunctionAccessingField").assertSuccessful();
  }

  @Test
  public void testPackageInlineFunctionFromOurPackage() throws Exception {
    performTest("kotlin/pureKotlin/packageInlineFunctionFromOurPackage").assertSuccessful();
  }

  @Test
  public void testPackagePrivateOnlyChanged() throws Exception {
    performTest("kotlin/pureKotlin/packagePrivateOnlyChanged").assertSuccessful();
  }

  @Test
  public void testPackageRecreated() throws Exception {
    performTest(2, "kotlin/pureKotlin/packageRecreated").assertSuccessful();
  }

  @Test
  public void testPackageRecreatedAfterRenaming() throws Exception {
    performTest(2, "kotlin/pureKotlin/packageRecreatedAfterRenaming").assertSuccessful();
  }

  @Test
  public void testPackageRemoved() throws Exception {
    performTest("kotlin/pureKotlin/packageRemoved").assertSuccessful();
  }

  @Test
  public void testParameterWithDefaultValueAdded() throws Exception {
    performTest(2, "kotlin/pureKotlin/parameterWithDefaultValueAdded").assertSuccessful();
  }

  @Test
  public void testParameterWithDefaultValueRemoved() throws Exception {
    performTest("kotlin/pureKotlin/parameterWithDefaultValueRemoved").assertSuccessful();
  }

  @Test
  public void testPrivateConstantsChanged() throws Exception {
    performTest("kotlin/pureKotlin/privateConstantsChanged").assertSuccessful();
  }

  @Test
  public void testPrivateMethodAdded() throws Exception {
    performTest("kotlin/pureKotlin/privateMethodAdded").assertSuccessful();
  }

  @Test
  public void testPrivateMethodDeleted() throws Exception {
    performTest("kotlin/pureKotlin/privateMethodDeleted").assertSuccessful();
  }

  @Test
  public void testPrivateMethodSignatureChanged() throws Exception {
    performTest("kotlin/pureKotlin/privateMethodSignatureChanged").assertSuccessful();
  }

  @Test
  public void testPrivateSecondaryConstructorAdded() throws Exception {
    performTest("kotlin/pureKotlin/privateSecondaryConstructorAdded").assertSuccessful();
  }

  @Test
  public void testPrivateSecondaryConstructorDeleted() throws Exception {
    performTest("kotlin/pureKotlin/privateSecondaryConstructorDeleted").assertSuccessful();
  }

  @Test
  public void testPrivateValAccessorChanged() throws Exception {
    performTest("kotlin/pureKotlin/privateValAccessorChanged").assertSuccessful();
  }

  @Test
  public void testPrivateValAdded() throws Exception {
    performTest("kotlin/pureKotlin/privateValAdded").assertSuccessful();
  }

  @Test
  public void testPrivateValDeleted() throws Exception {
    performTest("kotlin/pureKotlin/privateValDeleted").assertSuccessful();
  }

  @Test
  public void testPrivateValSignatureChanged() throws Exception {
    performTest("kotlin/pureKotlin/privateValSignatureChanged").assertSuccessful();
  }

  @Test
  public void testPrivateVarAdded() throws Exception {
    performTest("kotlin/pureKotlin/privateVarAdded").assertSuccessful();
  }

  @Test
  public void testPrivateVarDeleted() throws Exception {
    performTest("kotlin/pureKotlin/privateVarDeleted").assertSuccessful();
  }

  @Test
  public void testPrivateVarSignatureChanged() throws Exception {
    performTest("kotlin/pureKotlin/privateVarSignatureChanged").assertSuccessful();
  }

  @Test
  public void testPropertyRedeclaration() throws Exception {
    performTest("kotlin/pureKotlin/propertyRedeclaration").assertFailure();
  }

  @Test
  public void testPublicPropertyWithPrivateSetter() throws Exception {
    performTest("kotlin/pureKotlin/publicPropertyWithPrivateSetter").assertSuccessful();
  }

  @Test
  public void testRemoveAndRestoreCompanion() throws Exception {
    performTest(2, "kotlin/pureKotlin/removeAndRestoreCompanion").assertSuccessful();
  }

  @Test
  public void testRemoveAndRestoreCompanionWithImplicitUsages() throws Exception {
    performTest(2, "kotlin/pureKotlin/removeAndRestoreCompanionWithImplicitUsages").assertSuccessful();
  }

  @Test
  public void testRemoveClass() throws Exception {
    performTest("kotlin/pureKotlin/removeClass").assertSuccessful();
  }

  @Test
  public void testRemoveClassInDefaultPackage() throws Exception {
    performTest("kotlin/pureKotlin/removeClassInDefaultPackage").assertSuccessful();
  }

  @Test
  public void testRemoveFileWithFunctionOverload() throws Exception {
    performTest("kotlin/pureKotlin/removeFileWithFunctionOverload").assertSuccessful();
  }

  @Test
  public void testRemoveImportedRootExtensionProperty() throws Exception {
    performTest("kotlin/pureKotlin/removeImportedRootExtensionProperty").assertFailure();
  }

  @Test
  public void testRemoveImportedRootFunction() throws Exception {
    performTest("kotlin/pureKotlin/removeImportedRootFunction").assertFailure();
  }

  @Test
  public void testRemoveImportedRootProperty() throws Exception {
    performTest("kotlin/pureKotlin/removeImportedRootProperty").assertFailure();
  }

  @Test
  public void testRemoveMemberTypeAlias() throws Exception {
    performTest("kotlin/pureKotlin/removeMemberTypeAlias").assertSuccessful();
  }

  @Test
  public void testRemoveTopLevelTypeAlias() throws Exception {
    performTest("kotlin/pureKotlin/removeTopLevelTypeAlias").assertSuccessful();
  }

  @Test
  public void testRemoveUnusedFile() throws Exception {
    performTest("kotlin/pureKotlin/removeUnusedFile").assertSuccessful();
  }

  @Test
  public void testRenameClass() throws Exception {
    performTest("kotlin/pureKotlin/renameClass").assertSuccessful();
  }

  @Test
  public void testRenameFileWithClassesOnly() throws Exception {
    performTest("kotlin/pureKotlin/renameFileWithClassesOnly").assertSuccessful();
  }

  @Test
  public void testRenameFileWithFunctionOverload() throws Exception {
    performTest("kotlin/pureKotlin/renameFileWithFunctionOverload").assertSuccessful();
  }

  @Test
  public void testRenameFileWithFunctionOverloadAndCreateConflict() throws Exception {
    performTest("kotlin/pureKotlin/renameFileWithFunctionOverloadAndCreateConflict").assertFailure();
  }

  @Test
  public void testReturnTypeChanged() throws Exception {
    performTest("kotlin/pureKotlin/returnTypeChanged").assertSuccessful();
  }

  @Test
  public void testSamConversion() throws Exception {
    performTest("kotlin/pureKotlin/samConversion").assertSuccessful();
  }

  @Test
  public void testSealedClassesAddImplements() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesAddImplements").assertSuccessful();
  }

  @Test
  public void testSealedClassesAddIndirectInheritor() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesAddIndirectInheritor").assertSuccessful();
  }

  @Test
  public void testSealedClassesAddInheritor() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesAddInheritor").assertSuccessful();
  }

  @Test
  public void testSealedClassesRemoveImplements() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesRemoveImplements").assertSuccessful();
  }

  @Test
  public void testSealedClassesRemoveInheritor() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesRemoveInheritor").assertSuccessful();
  }

  @Test
  public void testSealedClassesWhenExpression() throws Exception {
    performTest("kotlin/pureKotlin/sealedClassesWhenExpression").assertSuccessful();
  }

  @Test
  public void testSecondaryConstructorInlined() throws Exception {
    performTest("kotlin/pureKotlin/secondaryConstructorInlined").assertSuccessful();
  }

  @Test
  public void testSequentualAddingAndDeletingOfPropertyAndUsage() throws Exception {
    performTest(5, "kotlin/pureKotlin/sequentualAddingAndDeletingOfPropertyAndUsage").assertFailure();
  }

  @Test
  public void testSerializedSubClassAndChangedInterfaces() throws Exception {
    performTest("kotlin/pureKotlin/serializedSubClassAndChangedInterfaces").assertSuccessful();
  }

  @Test
  public void testSimpleClassDependency() throws Exception {
    performTest("kotlin/pureKotlin/simpleClassDependency").assertSuccessful();
  }

  @Test
  public void testSoleFileChangesPackage() throws Exception {
    performTest("kotlin/pureKotlin/soleFileChangesPackage").assertSuccessful();
  }

  @Test
  public void testSubpackage() throws Exception {
    performTest("kotlin/pureKotlin/subpackage").assertSuccessful();
  }

  @Test
  public void testSuspendWithStateMachine() throws Exception {
    performTest("kotlin/pureKotlin/suspendWithStateMachine").assertSuccessful();
  }

  @Test
  public void testTopLevelFunctionSameSignature() throws Exception {
    performTest("kotlin/pureKotlin/topLevelFunctionSameSignature").assertSuccessful();
  }

  @Test
  public void testTopLevelMembersInTwoFiles() throws Exception {
    performTest("kotlin/pureKotlin/topLevelMembersInTwoFiles").assertSuccessful();
  }

  @Test
  public void testTopLevelPrivateValUsageAdded() throws Exception {
    performTest(2, "kotlin/pureKotlin/topLevelPrivateValUsageAdded").assertSuccessful();
  }

  @Test
  public void testTraitClassObjectConstantChanged() throws Exception {
    performTest("kotlin/pureKotlin/traitClassObjectConstantChanged").assertSuccessful();
  }

  @Test
  public void testTypealiasNameClashSinceK2() throws Exception {
    performTest("kotlin/pureKotlin/typealiasNameClash_SinceK2").assertFailure();
  }

  @Test
  public void testTypealiasNameClash2SinceK2() throws Exception {
    performTest("kotlin/pureKotlin/typealiasNameClash2_SinceK2").assertFailure();
  }

  @Test
  public void testUnwrapJvmFieldInJvmNameFromObject() throws Exception {
    performTest("kotlin/pureKotlin/unwrapJvmFieldInJvmNameFromObject").assertFailure();
  }

  @Test
  public void testValAddCustomAccessor() throws Exception {
    performTest("kotlin/pureKotlin/valAddCustomAccessor").assertFailure();
  }

  @Test
  public void testValPropertyBecameWritable() throws Exception {
    performTest("kotlin/pureKotlin/valPropertyBecameWritable").assertFailure();
  }

  @Test
  public void testValRemoveCustomAccessor() throws Exception {
    performTest(2, "kotlin/pureKotlin/valRemoveCustomAccessor").assertSuccessful();
  }

  @Test
  public void testWrapJvmFieldInJvmNameWithObject() throws Exception {
    performTest("kotlin/pureKotlin/wrapJvmFieldInJvmNameWithObject").assertFailure();
  }
}
