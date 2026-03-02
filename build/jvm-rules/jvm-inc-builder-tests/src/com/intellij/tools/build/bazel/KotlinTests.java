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

  // classHierarchyAffected tests

  @Test
  public void testAnnotationFlagRemoved() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/annotationFlagRemoved").assertSuccessful();
  }

  @Test
  public void testAnnotationListChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/annotationListChanged").assertSuccessful();
  }

  @Test
  public void testBridgeGenerated() throws Exception {
    performTest("kotlin/classHierarchyAffected/bridgeGenerated").assertSuccessful();
  }

  @Test
  public void testClassBecameFinal() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/classBecameFinal").assertSuccessful();
  }

  /*
    todo: investigate why no lookups are reported for empty "imports-only" *.kt files

  @Test
  public void testClassBecameInterface() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/classBecameInterface").assertSuccessful();
  }
  */

  /*
    todo: investigate why no lookups are reported for empty "imports-only" *.kt files

  @Test
  public void testClassBecamePrivate() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/classBecamePrivate").assertSuccessful();
  }
  */

  @Test
  public void testClassRemovedHierarchy() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/classRemoved").assertSuccessful();
  }

  @Test
  public void testMethodAdded() throws Exception {
    performTest("kotlin/classHierarchyAffected/methodAdded").assertSuccessful();
  }

  @Test
  public void testMethodRemoved() throws Exception {
    performTest("kotlin/classHierarchyAffected/methodRemoved").assertSuccessful();
  }

  /*
  todo: investigate why no lookups are reported for empty "imports-only" *.kt files

  @Test
  public void testSupertypesListChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/supertypesListChanged").assertSuccessful();
  }
  */

  @Test
  public void testClassMovedIntoOtherClass() throws Exception {
    performTest("kotlin/classHierarchyAffected/classMovedIntoOtherClass").assertSuccessful();
  }

  @Test
  public void testClassRemovedAndRestored() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/classRemovedAndRestored").assertSuccessful();
  }

  @Test
  public void testCompanionObjectInheritedMemberChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/companionObjectInheritedMemberChanged").assertSuccessful();
  }

  @Test
  public void testCompanionObjectMemberChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/companionObjectMemberChanged").assertSuccessful();
  }

  @Test
  public void testCompanionObjectNameChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/companionObjectNameChanged").assertSuccessful();
  }

  @Test
  public void testCompanionObjectToSimpleObject() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/companionObjectToSimpleObject").assertSuccessful();
  }

  @Test
  public void testConstructorVisibilityChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/constructorVisibilityChanged").assertSuccessful();
  }

  @Test
  public void testEnumEntryAdded() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/enumEntryAdded").assertSuccessful();
  }

  @Test
  public void testEnumEntryRemoved() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/enumEntryRemoved").assertSuccessful();
  }

  @Test
  public void testEnumMemberChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/enumMemberChanged").assertSuccessful();
  }

  @Test
  public void testFlagsAndMemberInDifferentClassesChanged() throws Exception {
    performTest("kotlin/classHierarchyAffected/flagsAndMemberInDifferentClassesChanged").assertSuccessful();
  }

  @Test
  public void testFlagsAndMemberInSameClassChanged() throws Exception {
    performTest(3, "kotlin/classHierarchyAffected/flagsAndMemberInSameClassChanged").assertSuccessful();
  }

  @Test
  public void testImplcitUpcast() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/implcitUpcast").assertSuccessful();
  }

  @Test
  public void testInferredTypeArgumentChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/inferredTypeArgumentChanged").assertSuccessful();
  }

  @Test
  public void testInferredTypeChanged() throws Exception {
    performTest("kotlin/classHierarchyAffected/inferredTypeChanged").assertSuccessful();
  }

  @Test
  public void testInterfaceAnyMethods() throws Exception {
    performTest("kotlin/classHierarchyAffected/interfaceAnyMethods").assertSuccessful();
  }

  @Test
  public void testLambdaParameterAffected() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/lambdaParameterAffected").assertSuccessful();
  }

  @Test
  public void testMethodAnnotationAdded() throws Exception {
    performTest("kotlin/classHierarchyAffected/methodAnnotationAdded").assertSuccessful();
  }

  @Test
  public void testMethodNullabilityChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/methodNullabilityChanged").assertSuccessful();
  }

  @Test
  public void testMethodParameterWithDefaultValueAdded() throws Exception {
    performTest("kotlin/classHierarchyAffected/methodParameterWithDefaultValueAdded").assertSuccessful();
  }

  @Test
  public void testOverrideExplicit() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/overrideExplicit").assertSuccessful();
  }

  @Test
  public void testOverrideImplicit() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/overrideImplicit").assertSuccessful();
  }

  @Test
  public void testPropertyNullabilityChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/propertyNullabilityChanged").assertSuccessful();
  }

  @Test
  public void testSealedClassImplAdded() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/sealedClassImplAdded").assertSuccessful();
  }

  @Test
  public void testSealedClassIndirectImplAdded() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/sealedClassIndirectImplAdded").assertSuccessful();
  }

  @Test
  public void testSealedClassNestedImplAdded() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/sealedClassNestedImplAdded").assertSuccessful();
  }

  @Test
  public void testSecondaryConstructorAdded() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/secondaryConstructorAdded").assertSuccessful();
  }

  @Test
  public void testStarProjectionUpperBoundChanged() throws Exception {
    performTest("kotlin/classHierarchyAffected/starProjectionUpperBoundChanged").assertSuccessful();
  }

  @Test
  public void testSyntheticMethodRemoved() throws Exception {
    performTest("kotlin/classHierarchyAffected/syntheticMethodRemoved").assertFailure();
  }

  @Test
  public void testTypeParameterListChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/typeParameterListChanged").assertSuccessful();
  }

  @Test
  public void testVarianceChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/varianceChanged").assertSuccessful();
  }

  @Test
  public void testWithIntermediateBodiesChanged() throws Exception {
    performTest(2, "kotlin/classHierarchyAffected/withIntermediateBodiesChanged").assertSuccessful();
  }

  // sealed tests

  @Test
  public void testSealedAddedEntry() throws Exception {
    performTest(2, "kotlin/sealed/addedEntry").assertSuccessful();
  }

  @Test
  public void testSealedRemovedEntry() throws Exception {
    performTest(2, "kotlin/sealed/removedEntry").assertSuccessful();
  }

  @Test
  public void testSealedUnrelatedDiff() throws Exception {
    performTest(2, "kotlin/sealed/unrelatedDiff").assertSuccessful();
  }

  // scopeExpansion tests

  @Test
  public void testChangeTypeAliasAndUsage() throws Exception {
    performTest("kotlin/scopeExpansion/changeTypeAliasAndUsage").assertSuccessful();
  }

  @Test
  public void testProtectedBecomesInternal() throws Exception {
    performTest("kotlin/scopeExpansion/protectedBecomesInternal").assertSuccessful();
  }

  @Test
  public void testProtectedBecomesPublicAccessedTroughChild() throws Exception {
    performTest("kotlin/scopeExpansion/protectedBecomesPublicAccessedTroughChild").assertSuccessful();
  }

  // resolution tests

  @Test
  public void testAddMethodDirectlyImplicitThis() throws Exception {
    performTest(2, "kotlin/resolution/addMethodDirectly_implicitThis").assertSuccessful();
  }

  @Test
  public void testAddMethodToParentImplicitThis() throws Exception {
    performTest(2, "kotlin/resolution/addMethodToParent_implicitThis").assertSuccessful();
  }

  @Test
  public void testClassOverFun() throws Exception {
    performTest(2, "kotlin/resolution/classOverFun").assertSuccessful();
  }

  @Test
  public void testInvokeOverFun() throws Exception {
    performTest(2, "kotlin/resolution/invokeOverFun").assertSuccessful();
  }

  // inlineFunCallSite tests

  @Test
  public void testInlineCallSiteClassProperty() throws Exception {
    performTest("kotlin/inlineFunCallSite/classProperty").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteCompanionObjectProperty() throws Exception {
    performTest("kotlin/inlineFunCallSite/companionObjectProperty").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteCoroutine() throws Exception {
    performTest("kotlin/inlineFunCallSite/coroutine").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteFunction() throws Exception {
    performTest("kotlin/inlineFunCallSite/function").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteGetter() throws Exception {
    performTest("kotlin/inlineFunCallSite/getter").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteLambda() throws Exception {
    performTest("kotlin/inlineFunCallSite/lambda").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteLocalFun() throws Exception {
    performTest("kotlin/inlineFunCallSite/localFun").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteMethod() throws Exception {
    performTest("kotlin/inlineFunCallSite/method").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteParameterDefaultValue() throws Exception {
    performTest("kotlin/inlineFunCallSite/parameterDefaultValue").assertSuccessful();
  }

  @Test
  public void testInlineCallSitePrimaryConstructorParameterDefaultValue() throws Exception {
    performTest("kotlin/inlineFunCallSite/primaryConstructorParameterDefaultValue").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteSuperCall() throws Exception {
    performTest("kotlin/inlineFunCallSite/superCall").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteThisCall() throws Exception {
    performTest("kotlin/inlineFunCallSite/thisCall").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteTopLevelObjectProperty() throws Exception {
    performTest("kotlin/inlineFunCallSite/topLevelObjectProperty").assertSuccessful();
  }

  @Test
  public void testInlineCallSiteTopLevelProperty() throws Exception {
    performTest("kotlin/inlineFunCallSite/topLevelProperty").assertSuccessful();
  }

  // incrementalJvmCompilerOnly tests

  @Test
  public void testInlineFunctionSmapStability() throws Exception {
    performTest("kotlin/incrementalJvmCompilerOnly/inlineFunctionSmapStability").assertSuccessful();
  }

  @Test
  public void testInlineFunctionRegeneratedObjectStability() throws Exception {
    performTest("kotlin/incrementalJvmCompilerOnly/inlineFunctionRegeneratedObjectStability").assertSuccessful();
  }

  // withJava/other tests

  @Test
  public void testAccessingFunctionsViaRenamedFileClass() throws Exception {
    performTest("kotlin/withJava/other/accessingFunctionsViaRenamedFileClass").assertSuccessful();
  }

  @Test
  public void testClassRedeclaration() throws Exception {
    performTest("kotlin/withJava/other/classRedeclaration").assertFailure();
  }

  @Test
  public void testDefaultValueInConstructorAdded() throws Exception {
    performTest("kotlin/withJava/other/defaultValueInConstructorAdded").assertSuccessful();
  }

  @Test
  public void testJvmNameChanged() throws Exception {
    performTest("kotlin/withJava/other/jvmNameChanged").assertSuccessful();
  }

  @Test
  public void testMainRedeclaration() throws Exception {
    performTest("kotlin/withJava/other/mainRedeclaration").assertSuccessful();
  }

  @Test
  public void testOptionalParameter() throws Exception {
    performTest("kotlin/withJava/other/optionalParameter").assertSuccessful();
  }

  @Test
  public void testAllKotlinFilesRemovedThenNewAdded() throws Exception {
    performTest(2, "kotlin/withJava/other/allKotlinFilesRemovedThenNewAdded").assertSuccessful();
  }

  @Test
  public void testClassToPackageFacade() throws Exception {
    performTest(2, "kotlin/withJava/other/classToPackageFacade").assertSuccessful();
  }

  @Test
  public void testConflictingPlatformDeclarations() throws Exception {
    performTest("kotlin/withJava/other/conflictingPlatformDeclarations").assertFailure();
  }

  @Test
  public void testInlineFunctionWithJvmNameInClass() throws Exception {
    performTest("kotlin/withJava/other/inlineFunctionWithJvmNameInClass").assertSuccessful();
  }

  @Test
  public void testInlineTopLevelFunctionWithJvmName() throws Exception {
    performTest("kotlin/withJava/other/inlineTopLevelFunctionWithJvmName").assertSuccessful();
  }

  @Test
  public void testInlineTopLevelValPropertyWithJvmName() throws Exception {
    performTest("kotlin/withJava/other/inlineTopLevelValPropertyWithJvmName").assertSuccessful();
  }

  @Test
  public void testInnerClassNotGeneratedWhenRebuilding() throws Exception {
    performTest("kotlin/withJava/other/innerClassNotGeneratedWhenRebuilding").assertSuccessful();
  }

  @Test
  public void testMultifileClassAddTopLevelFunWithDefault() throws Exception {
    performTest("kotlin/withJava/other/multifileClassAddTopLevelFunWithDefault").assertSuccessful();
  }

  @Test
  public void testMultifileClassFileAdded() throws Exception {
    performTest("kotlin/withJava/other/multifileClassFileAdded").assertSuccessful();
  }

  @Test
  public void testMultifileClassFileChanged() throws Exception {
    performTest("kotlin/withJava/other/multifileClassFileChanged").assertSuccessful();
  }

  @Test
  public void testMultifileClassFileMovedToAnotherMultifileClass() throws Exception {
    performTest("kotlin/withJava/other/multifileClassFileMovedToAnotherMultifileClass").assertSuccessful();
  }

  @Test
  public void testMultifileClassInlineFunction() throws Exception {
    performTest("kotlin/withJava/other/multifileClassInlineFunction").assertSuccessful();
  }

  @Test
  public void testMultifileClassInlineFunctionAccessingField() throws Exception {
    performTest("kotlin/withJava/other/multifileClassInlineFunctionAccessingField").assertSuccessful();
  }

  @Test
  public void testMultifileClassRecreated() throws Exception {
    performTest(2, "kotlin/withJava/other/multifileClassRecreated").assertSuccessful();
  }

  @Test
  public void testMultifileClassRecreatedAfterRenaming() throws Exception {
    performTest(2, "kotlin/withJava/other/multifileClassRecreatedAfterRenaming").assertSuccessful();
  }

  @Test
  public void testMultifileClassRemoved() throws Exception {
    performTest("kotlin/withJava/other/multifileClassRemoved").assertSuccessful();
  }

  @Test
  public void testMultifileDependantUsage() throws Exception {
    performTest("kotlin/withJava/other/multifileDependantUsage").assertFailure();
  }

  @Test
  public void testMultifilePackagePartMethodAdded() throws Exception {
    performTest("kotlin/withJava/other/multifilePackagePartMethodAdded").assertSuccessful();
  }

  @Test
  public void testMultifilePartsWithProperties() throws Exception {
    performTest("kotlin/withJava/other/multifilePartsWithProperties").assertSuccessful();
  }

  @Test
  public void testPackageFacadeToClass() throws Exception {
    performTest(2, "kotlin/withJava/other/packageFacadeToClass").assertSuccessful();
  }

  @Test
  public void testPackageMultifileClassOneFileWithPublicChanges() throws Exception {
    performTest("kotlin/withJava/other/packageMultifileClassOneFileWithPublicChanges").assertSuccessful();
  }

  @Test
  public void testPackageMultifileClassPrivateOnlyChanged() throws Exception {
    performTest("kotlin/withJava/other/packageMultifileClassPrivateOnlyChanged").assertSuccessful();
  }

  @Test
  public void testPublicPropertyWithPrivateSetterMultiFileFacade() throws Exception {
    performTest("kotlin/withJava/other/publicPropertyWithPrivateSetterMultiFileFacade").assertSuccessful();
  }

  @Test
  public void testTopLevelFunctionWithJvmName() throws Exception {
    performTest("kotlin/withJava/other/topLevelFunctionWithJvmName").assertSuccessful();
  }

  @Test
  public void testTopLevelPropertyWithJvmName() throws Exception {
    performTest("kotlin/withJava/other/topLevelPropertyWithJvmName").assertSuccessful();
  }

  // withJava/javaUsedInKotlin tests

  @Test
  public void testChangeFieldType() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeFieldType").assertSuccessful();
  }

  @Test
  public void testChangeSignature() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSignature").assertSuccessful();
  }

  @Test
  public void testChangeSignatureStatic() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSignatureStatic").assertSuccessful();
  }

  @Test
  public void testChangeSignaturePackagePrivate() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSignaturePackagePrivate").assertSuccessful();
  }

  @Test
  public void testChangeSignaturePackagePrivateNonRoot() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSignaturePackagePrivateNonRoot").assertFailure();
  }

  @Test
  public void testChangeSyntheticProperty() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSyntheticProperty").assertFailure();
  }

  @Test
  public void testChangeSyntheticProperty2() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSyntheticProperty2").assertSuccessful();
  }

  @Test
  public void testChangeSyntheticProperty3() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeSyntheticProperty3").assertSuccessful();
  }

  @Test
  public void testChangePropertyOverrideType() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changePropertyOverrideType").assertSuccessful();
  }

  @Test
  public void testConstantChanged() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/constantChanged").assertSuccessful();
  }

  @Test
  public void testConstantUnchanged() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/constantUnchanged").assertSuccessful();
  }

  @Test
  public void testConstantPropertyChanged() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/constantPropertyChanged").assertSuccessful();
  }

  @Test
  public void testJavaEnumEntryAdded() throws Exception {
    performTest(2, "kotlin/withJava/javaUsedInKotlin/enumEntryAdded").assertSuccessful();
  }

  @Test
  public void testJavaEnumEntryRemoved() throws Exception {
    performTest(2, "kotlin/withJava/javaUsedInKotlin/enumEntryRemoved").assertSuccessful();
  }

  @Test
  public void testJavaAndKotlinChangedSimultaneously() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/javaAndKotlinChangedSimultaneously").assertSuccessful();
  }

  @Test
  public void testJavaFieldNullabilityChanged() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/javaFieldNullabilityChanged").assertSuccessful();
  }

  @Test
  public void testJavaMethodParamNullabilityChanged() throws Exception {
    performTest(2, "kotlin/withJava/javaUsedInKotlin/javaMethodParamNullabilityChanged").assertSuccessful();
  }

  @Test
  public void testJavaMethodReturnTypeNullabilityChanged() throws Exception {
    performTest(2, "kotlin/withJava/javaUsedInKotlin/javaMethodReturnTypeNullabilityChanged").assertSuccessful();
  }

  @Test
  public void testMethodRenamed() throws Exception {
    performTest(2, "kotlin/withJava/javaUsedInKotlin/methodRenamed").assertSuccessful();
  }

  @Test
  public void testNotChangeSignature() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/notChangeSignature").assertSuccessful();
  }

  @Test
  public void testAddClashingFunToParent() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/addClashingFunToParent").assertFailure();
  }

  @Test
  public void testAddNullableAnnotation() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/addNullableAnnotation").assertFailure();
  }

  @Test
  public void testAddPurelyImplementsAnnotation() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/addPurelyImplementsAnnotation").assertFailure();
  }

  @Test
  public void testChangeGetterType() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeGetterType").assertFailure();
  }

  @Test
  public void testChangeMethodToPropertyInInheritance() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeMethodToPropertyInInheritance").assertFailure();
  }

  @Test
  public void testChangeNotUsedSignature() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/changeNotUsedSignature").assertSuccessful();
  }

  @Test
  public void testMethodAddedInSuper() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/methodAddedInSuper").assertFailure();
  }

  @Test
  public void testPotentialSamAdapter() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/potentialSamAdapter").assertSuccessful();
  }

  @Test
  public void testRawErrorTypeDuringSerialization() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/rawErrorTypeDuringSerialization").assertSuccessful();
  }

  @Test
  public void testRemoveAnnotation() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/removeAnnotation").assertFailure();
  }

  @Test
  public void testRemoveGetter() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/removeGetter").assertFailure();
  }

  @Test
  public void testSamConversionMethodAddDefault() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/samConversions/methodAddDefault").assertFailure();
  }

  @Test
  public void testSamConversionMethodAdded() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/samConversions/methodAdded").assertFailure();
  }

  @Test
  public void testSamConversionMethodAddedSamAdapter() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/samConversions/methodAddedSamAdapter").assertFailure();
  }

  @Test
  public void testSamConversionMethodSignatureChanged() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/samConversions/methodSignatureChanged").assertSuccessful();
  }

  @Test
  public void testSamConversionMethodSignatureChangedSamAdapter() throws Exception {
    performTest("kotlin/withJava/javaUsedInKotlin/samConversions/methodSignatureChangedSamAdapter").assertSuccessful();
  }

  @Test
  public void testMixedInheritance() throws Exception {
    performTest(7, "kotlin/withJava/javaUsedInKotlin/mixedInheritance").assertFailure();
  }

  // incrementalJvmCompilerOnly tests (Java+Kotlin)

  @Test
  public void testAddAnnotationToJavaClass() throws Exception {
    performTest("kotlin/incrementalJvmCompilerOnly/addAnnotationToJavaClass").assertSuccessful();
  }

  /*
  todo: seems like kotlin imports tracker never reports on-demand imports, so logic relying on them may miss dependencies

  @Test
  public void testAddNestedClass() throws Exception {
    performTest("kotlin/incrementalJvmCompilerOnly/addNestedClass").assertFailure();
  }
  */

  @Test
  public void testChangeAnnotationInJavaClass() throws Exception {
    performTest("kotlin/incrementalJvmCompilerOnly/changeAnnotationInJavaClass").assertSuccessful();
  }

  // custom tests

  @Test
  public void testJavaConstantChangedUsedInKotlin() throws Exception {
    performTest("kotlin/custom/javaConstantChangedUsedInKotlin").assertSuccessful();
  }

  @Test
  public void testJavaConstantUnchangedUsedInKotlin() throws Exception {
    performTest("kotlin/custom/javaConstantUnchangedUsedInKotlin").assertSuccessful();
  }

  @Test
  public void testKotlinConstantChangedUsedInJava() throws Exception {
    performTest("kotlin/custom/kotlinConstantChangedUsedInJava").assertSuccessful();
  }

  @Test
  public void testKotlinConstantUnchangedUsedInJava() throws Exception {
    performTest("kotlin/custom/kotlinConstantUnchangedUsedInJava").assertSuccessful();
  }

  @Test
  public void testKotlinJvmFieldChangedUsedInJava() throws Exception {
    performTest("kotlin/custom/kotlinJvmFieldChangedUsedInJava").assertSuccessful();
  }

  @Test
  public void testKotlinJvmFieldUnchangedUsedInJava() throws Exception {
    performTest("kotlin/custom/kotlinJvmFieldUnchangedUsedInJava").assertSuccessful();
  }

  // multiModule/common tests

  /*
    todo: investigate why no lookups are reported for empty "imports-only" *.kt files

  @Test
  public void testMultiModuleSimple() throws Exception {
    performTest(2, "kotlin/multiModule/common/simple").assertSuccessful();
  }
  */

  @Test
  public void testMultiModuleSimpleDependency() throws Exception {
    performTest("kotlin/multiModule/common/simpleDependency").assertSuccessful();
  }

  @Test
  public void testMultiModuleSimpleDependencyUnchanged() throws Exception {
    performTest("kotlin/multiModule/common/simpleDependencyUnchanged").assertSuccessful();
  }

  @Test
  public void testMultiModuleConstantValueChanged() throws Exception {
    performTest("kotlin/multiModule/common/constantValueChanged").assertSuccessful();
  }

  @Test
  public void testMultiModuleClassAdded() throws Exception {
    performTest(2, "kotlin/multiModule/common/classAdded").assertSuccessful();
  }

  @Test
  public void testMultiModuleClassRemoved() throws Exception {
    performTest(2, "kotlin/multiModule/common/classRemoved").assertSuccessful();
  }

  @Test
  public void testMultiModuleTransitiveDependency() throws Exception {
    performTest("kotlin/multiModule/common/transitiveDependency").assertSuccessful();
  }

  @Test
  public void testMultiModuleTwoDependants() throws Exception {
    performTest("kotlin/multiModule/common/twoDependants").assertSuccessful();
  }

  @Test
  public void testMultiModuleCopyFileToAnotherModule() throws Exception {
    performTest("kotlin/multiModule/common/copyFileToAnotherModule").assertSuccessful();
  }

  @Test
  public void testMultiModuleMoveFileToAnotherModule() throws Exception {
    performTest("kotlin/multiModule/common/moveFileToAnotherModule").assertSuccessful();
  }

  @Test
  public void testMultiModuleDuplicatedClass() throws Exception {
    performTest("kotlin/multiModule/common/duplicatedClass").assertSuccessful();
  }

  @Test
  public void testMultiModuleFunctionFromDifferentPackageChanged() throws Exception {
    performTest("kotlin/multiModule/common/functionFromDifferentPackageChanged").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultParameterAdded() throws Exception {
    performTest("kotlin/multiModule/common/defaultParameterAdded").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultParameterRemoved() throws Exception {
    performTest("kotlin/multiModule/common/defaultParameterRemoved").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultParameterAddedForTopLevelFun() throws Exception {
    performTest("kotlin/multiModule/common/defaultParameterAddedForTopLevelFun").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultParameterRemovedForTopLevelFun() throws Exception {
    performTest("kotlin/multiModule/common/defaultParameterRemovedForTopLevelFun").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultArgumentInConstructorRemoved() throws Exception {
    performTest(2, "kotlin/multiModule/common/defaultArgumentInConstructorRemoved").assertSuccessful();
  }

  @Test
  public void testMultiModuleDefaultValueInConstructorRemoved() throws Exception {
    performTest(2, "kotlin/multiModule/common/defaultValueInConstructorRemoved").assertSuccessful();
  }

  @Test
  public void testMultiModuleSimpleDependencyErrorOnAccessToInternal1() throws Exception {
    performTest("kotlin/multiModule/common/simpleDependencyErrorOnAccessToInternal1").assertFailure();
  }

  @Test
  public void testMultiModuleSimpleDependencyErrorOnAccessToInternal2() throws Exception {
    performTest("kotlin/multiModule/common/simpleDependencyErrorOnAccessToInternal2").assertFailure();
  }

  @Test
  public void testMultiModuleInlineFunctionInlined() throws Exception {
    performTest("kotlin/multiModule/common/inlineFunctionInlined").assertSuccessful();
  }

  @Test
  public void testMultiModuleInlineFunctionTwoPackageParts() throws Exception {
    performTest(2, "kotlin/multiModule/common/inlineFunctionTwoPackageParts").assertSuccessful();
  }

  @Test
  public void testMultiModuleTransitiveInlining() throws Exception {
    performTest("kotlin/multiModule/common/transitiveInlining").assertSuccessful();
  }

  @Test
  public void testMultiModuleExportedDependency() throws Exception {
    performTest(2, "kotlin/multiModule/common/exportedDependency").assertSuccessful();
  }

  // multiModule/withJavaUsedInKotlin tests

  @Test
  public void testMultiModuleImportedClassRemoved() throws Exception {
    performTest("kotlin/multiModule/withJavaUsedInKotlin/importedClassRemoved").assertFailure();
  }

  // lazyKotlinCaches tests

  @Test
  public void testLazyKotlinCachesClass() throws Exception {
    performTest("kotlin/lazyKotlinCaches/class").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesClassInheritance() throws Exception {
    performTest("kotlin/lazyKotlinCaches/classInheritance").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesConstant() throws Exception {
    performTest("kotlin/lazyKotlinCaches/constant").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesFunction() throws Exception {
    performTest("kotlin/lazyKotlinCaches/function").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesInlineFunctionWithoutUsage() throws Exception {
    performTest("kotlin/lazyKotlinCaches/inlineFunctionWithoutUsage").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesInlineFunctionWithUsage() throws Exception {
    performTest("kotlin/lazyKotlinCaches/inlineFunctionWithUsage").assertSuccessful();
  }

  @Test
  public void testLazyKotlinCachesTopLevelPropertyAccess() throws Exception {
    performTest("kotlin/lazyKotlinCaches/topLevelPropertyAccess").assertSuccessful();
  }

  // withJava/kotlinUsedInJava tests

  @Test
  public void testKotlinUsedInJavaAddOptionalParameter() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/addOptionalParameter").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaChangeNotUsedSignature() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/changeNotUsedSignature").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaChangeSignature() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/changeSignature").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaConstantChanged() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/constantChanged").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaConstantUnchanged() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/constantUnchanged").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaFunRenamed() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/funRenamed").assertFailure();
  }

  @Test
  public void testKotlinUsedInJavaImportedClassRemoved() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/importedClassRemoved").assertFailure();
  }

  @Test
  public void testKotlinUsedInJavaJvmFieldChanged() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/jvmFieldChanged").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaJvmFieldUnchanged() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/jvmFieldUnchanged").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaMethodAddedInSuper() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/methodAddedInSuper").assertFailure();
  }

  @Test
  public void testKotlinUsedInJavaNotChangeSignature() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/notChangeSignature").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaOnlyTopLevelFunctionInFileRemoved() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/onlyTopLevelFunctionInFileRemoved").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaPackageFileAdded() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/packageFileAdded").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaPrivateChanges() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/privateChanges").assertSuccessful();
  }

  @Test
  public void testKotlinUsedInJavaPropertyRenamed() throws Exception {
    performTest("kotlin/withJava/kotlinUsedInJava/propertyRenamed").assertFailure();
  }

  // withJava/convertBetweenJavaAndKotlin tests

  @Test
  public void testJavaToKotlin() throws Exception {
    performTest("kotlin/withJava/convertBetweenJavaAndKotlin/javaToKotlin").assertSuccessful();
  }

  @Test
  public void testJavaToKotlinAndBack() throws Exception {
    performTest(2, "kotlin/withJava/convertBetweenJavaAndKotlin/javaToKotlinAndBack").assertSuccessful();
  }

  @Test
  public void testJavaToKotlinAndRemove() throws Exception {
    performTest(2, "kotlin/withJava/convertBetweenJavaAndKotlin/javaToKotlinAndRemove").assertSuccessful();
  }

  @Test
  public void testKotlinToJava() throws Exception {
    performTest("kotlin/withJava/convertBetweenJavaAndKotlin/kotlinToJava").assertSuccessful();
  }
}
