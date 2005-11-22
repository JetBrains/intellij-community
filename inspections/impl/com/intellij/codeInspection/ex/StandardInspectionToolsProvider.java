package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.i18n.I18nInspection;
import com.intellij.codeInsight.i18n.InvalidPropertyKeyInspection;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.dependencyViolation.DependencyInspection;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.codeInspection.htmlInspections.HtmlStyleLocalInspection;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.unneededThrows.UnusedThrowsDeclaration;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;
import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.lang.properties.UnusedPropertyInspection;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.xml.util.CheckImageSizeInspection;

/**
 * @author max
 */
public class StandardInspectionToolsProvider implements InspectionToolProvider, ApplicationComponent {
  public String getComponentName() {
    return "StandardInspectionToolsProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public Class[] getInspectionClasses() {
    return new Class[] {
      com.intellij.codeInspection.deadCode.DeadCodeInspection.class,
      com.intellij.codeInspection.visibility.VisibilityInspection.class,
      com.intellij.codeInspection.canBeStatic.CanBeStaticInspection.class,
      com.intellij.codeInspection.canBeFinal.CanBeFinalInspection.class,
      com.intellij.codeInspection.unusedParameters.UnusedParametersInspection.class,
      com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection.class,
      com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue.class,
      com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection.class,
      com.intellij.codeInspection.emptyMethod.EmptyMethodInspection.class,
      com.intellij.codeInspection.unneededThrows.UnneededThrows.class,

      com.intellij.codeInspection.dataFlow.DataFlowInspection.class,
      com.intellij.codeInspection.defUse.DefUseInspection.class,
      com.intellij.codeInspection.redundantCast.RedundantCastInspection.class,
      com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection.class,
      com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection.class,
      com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection.class,
      com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal.class,

      com.intellij.codeInspection.javaDoc.JavaDocLocalInspection.class,
      com.intellij.codeInspection.deprecation.DeprecationInspection.class,
      com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode.class,
      com.intellij.codeInspection.ejb.EJBInspection.class,

      com.intellij.codeInspection.java15api.Java15APIUsageInspection.class,

      I18nInspection.class,
      InvalidPropertyKeyInspection.class,
      UnusedPropertyInspection.class,

      DependencyInspection.class,
      FieldCanBeLocalInspection.class,
      NullableStuffInspection.class,

      DuplicateStringLiteralInspection.class,
      DuplicatePropertyInspection.class,
      UnusedMessageFormatParameterInspection.class,
      CheckImageSizeInspection.class,
      WrongPackageStatementInspection.class,
      DependencyInspection.class,
      SillyAssignmentInspection.class,
      UnusedThrowsDeclaration.class,
      AccessStaticViaInstance.class,
      HtmlStyleLocalInspection.class
      };
  }
}
