/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.codeInspection.NumericOverflowInspection;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.codeInspection.concurrencyAnnotations.*;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.codeInspection.dependencyViolation.DependencyInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.codeInspection.inconsistentLanguageLevel.InconsistentLanguageLevelInspection;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.intellij.codeInspection.testOnly.TestOnlyInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrows;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedLibraries.UnusedLibrariesInspection;
import com.intellij.codeInspection.unusedParameters.UnusedParametersInspection;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;

/**
 * @author max
 */
public class StandardInspectionToolsProvider implements InspectionToolProvider {

  public Class[] getInspectionClasses() {
    return new Class[] {
      DeadCodeInspection.class,
      UnusedLibrariesInspection.class,
      InconsistentLanguageLevelInspection.class,
      VisibilityInspection.class,
      CanBeFinalInspection.class,
      UnusedParametersInspection.class,
      SameParameterValueInspection.class,
      UnusedReturnValue.class,
      SameReturnValueInspection.class,
      EmptyMethodInspection.class,
      RedundantThrows.class,

      DataFlowInspection.class,
      DefUseInspection.class,
      NumericOverflowInspection.class,
      RedundantCastInspection.class,
      RedundantTypeArgsInspection.class,
      RedundantArrayForVarargsCallInspection.class,
      SuspiciousCollectionsMethodCallsInspection.class,
      LocalCanBeFinal.class,

      JavaDocLocalInspection.class,
      JavaDocReferenceInspection.class,
      DeprecationInspection.class,
      EqualsAndHashcode.class,

      Java15APIUsageInspection.class,

      DependencyInspection.class,
      FieldCanBeLocalInspection.class,
      NullableStuffInspection.class,
      TestOnlyInspection.class,

      WrongPackageStatementInspection.class,
      SillyAssignmentInspection.class,
      RedundantThrowsDeclaration.class,
      AccessStaticViaInstance.class,
      DefaultFileTemplateUsageInspection.class,
      UnnecessaryModuleDependencyInspection.class,
      RedundantSuppressInspection.class,
      UnusedSymbolLocalInspection.class,
      UnusedImportLocalInspection.class,
      UncheckedWarningLocalInspection.class,
      SuspiciousNameCombinationInspection.class,
      DuplicateThrowsInspection.class,
      LossyEncodingInspection.class,

      FieldAccessNotGuardedInspection.class,
      InstanceGuardedByStaticInspection.class,
      NonFinalFieldInImmutableInspection.class,
      NonFinalGuardInspection.class,
      StaticGuardedByInstanceInspection.class,
      UnknownGuardInspection.class
    };
  }
}
