package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.codeInspection.dependencyViolation.DependencyInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.codeInspection.htmlInspections.HtmlStyleLocalInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.i18n.InvalidPropertyKeyInspection;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.jsp.UnhandledExceptionInJSP;
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
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrows;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedParameters.UnusedParametersInspection;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;
import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;
import com.intellij.lang.properties.UnusedPropertyInspection;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.xml.util.CheckEmptyScriptTagInspection;
import com.intellij.xml.util.CheckImageSizeInspection;
import com.intellij.xml.util.CheckValidXmlInScriptBodyInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class StandardInspectionToolsProvider implements InspectionToolProvider, ApplicationComponent {
  @NotNull
  public String getComponentName() {
    return "StandardInspectionToolsProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }

  public Class[] getInspectionClasses() {
    return new Class[] {
      DeadCodeInspection.class,
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
      CheckEmptyScriptTagInspection.class,
      CheckValidXmlInScriptBodyInspection.class,
      WrongPackageStatementInspection.class,
      SillyAssignmentInspection.class,
      RedundantThrowsDeclaration.class,
      AccessStaticViaInstance.class,
      HtmlStyleLocalInspection.class,
      RequiredAttributesInspection.class,
      DefaultFileTemplateUsageInspection.class,
      UnnecessaryModuleDependencyInspection.class,
      UnhandledExceptionInJSP.class,
      RedundantSuppressInspection.class,
      UnusedSymbolLocalInspection.class,
      UnusedImportLocalInspection.class,
      UncheckedWarningLocalInspection.class,
      SuspiciousNameCombinationInspection.class,
      DuplicateThrowsInspection.class,
    };
  }
}
