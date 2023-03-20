// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.vmOptions.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VmOptionsCompletionContributorTest extends LightPlatformCodeInsightFixture4TestCase {
  @Test
  public void testEmptyPrompt() {
    configure("<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("--add-exports", "--add-opens",
                         "-agentlib:", "-agentpath:", "-D", "-da", "-disableassertions", "-Djava.awt.headless=",
                         "-dsa", "-Duser.dir=", "-Duser.home=", "-Duser.name=", "-ea", "-enableassertions", "-esa",
                         "-javaagent:", "-Xmx", "-XX:"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
    checkPresentation(myFixture.getLookupElements()[2], "-agentlib:|null/null");
  }

  @Test
  public void testSimpleOptions() {
    configure("-<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("-add-exports", "-add-opens",
                         "agentlib:", "agentpath:", "D", "da", "disableassertions", "Djava.awt.headless=",
                         "dsa", "Duser.dir=", "Duser.home=", "Duser.name=", "ea", "enableassertions", "esa",
                         "javaagent:", "Xmx", "XX:"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
    checkPresentation(myFixture.getLookupElements()[2], "-agentlib:|null/null");
  }

  @Test
  public void testDoubleDash() {
    configure("--<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("add-exports", "add-opens"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
  }

  @Test
  public void testXX() {
    configure("-XX:<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("+MinusFlag", "-Flag", "Diagnostic", "Experimental", "Value"), myFixture.getLookupElementStrings());
    LookupElement[] elements = myFixture.getLookupElements();
    checkPresentation(elements[0], "+MinusFlag|null/bool");
    checkPresentation(elements[1], "-Flag|null/bool");
    checkPresentation(elements[2], "[d] Diagnostic| = 20/uint");
    checkPresentation(elements[3], "[e] Experimental| = 10/uint");
    checkPresentation(elements[4], "Value| = 10/uint");
    myFixture.type('\n');
    myFixture.checkResult("-XX:+MinusFlag ");
  }

  private static void checkPresentation(LookupElement element, String expected) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    element.renderElement(presentation);
    Icon icon = presentation.getIcon();
    String iconTag = icon == AllIcons.General.ShowInfos ? "[d] " :
                     icon == AllIcons.General.ShowWarning ? "[e] " : "";
    String actual = iconTag + presentation.getItemText() + "|" + presentation.getTailText() + "/" + presentation.getTypeText();
    assertEquals(expected, actual);
  }

  @Test
  public void testXXValue() {
    configure("-XX:Val<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("-XX:Value=");
  }

  @Test
  public void testXXDiagnostic() {
    configure("-XX:Diag<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            -XX:+UnlockDiagnosticVMOptions
                            -XX:Diagnostic""");
  }

  @Test
  public void testXXExperimental() {
    configure("-XX:+Flag -XX:Exper<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            -XX:+UnlockExperimentalVMOptions
                            -XX:+Flag -XX:Experimental""");
  }

  @Test
  public void testXXExperimentalAlready() {
    configure("-XX:+Flag -XX:+UnlockExperimentalVMOptions -XX:Exper<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("-XX:+Flag -XX:+UnlockExperimentalVMOptions -XX:Experimental");
  }

  @Test
  public void testXXPlus() {
    configure("-XX:+<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("Flag", "MinusFlag"), myFixture.getLookupElementStrings());
  }

  @Test
  public void testXXAlreadyPresent() {
    configure("-XX:+Flag -XX:+<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("MinusFlag"), myFixture.getLookupElementStrings());
  }
  
  @Test
  public void testVmOptionDocumentation() {
    VMOption option = new VMOption("Flag", "bool", "true", VMOptionKind.Experimental, "SuperOption", VMOptionVariant.XX);
    DocumentationData result = (DocumentationData)option.computeDocumentation();
    String doc = result.getHtml();
    assertEquals("""
                   <table>\
                   <tr><td align="right" valign="top"><b>Option: </b></td><td>-XX:Flag</td></tr>\
                   <tr><td align="right" valign="top"><b>Category: </b></td><td>Experimental (requires -XX:+UnlockExperimentalVMOptions)</td></tr>\
                   <tr><td align="right" valign="top"><b>Type: </b></td><td>bool</td></tr>\
                   <tr><td align="right" valign="top"><b>Default value: </b></td><td>true</td></tr>\
                   <tr><td align="right" valign="top"><b>Description: </b></td><td>SuperOption</td></tr></table>""", doc);
  }

  private void configure(String text) {
    myFixture.configureByText(PlainTextFileType.INSTANCE, text);
    ServiceContainerUtil
      .replaceService(ApplicationManager.getApplication(), VMOptionsService.class, new TestVMOptionService(), getTestRootDisposable());
    ApplicationConfiguration configuration = new ApplicationConfiguration("Hello", getProject());
    configuration.setAlternativeJrePathEnabled(true);
    configuration.setAlternativeJrePath("/my/jre");
    Document document = myFixture.getEditor().getDocument();
    document.putUserData(VmOptionsEditor.SETTINGS_KEY, configuration);
  }

  private static class TestVMOptionService implements VMOptionsService {
    @NotNull
    @Override
    public CompletableFuture<JdkOptionsData> getOrComputeOptionsForJdk(@NotNull String javaHome) {
      assertEquals("/my/jre", javaHome);
      return CompletableFuture.completedFuture(new JdkOptionsData(List.of(
        new VMOption("Flag", "bool", "true", VMOptionKind.Product, null, VMOptionVariant.XX),
        new VMOption("MinusFlag", "bool", "false", VMOptionKind.Product, null, VMOptionVariant.XX),
        new VMOption("Value", "uint", "10", VMOptionKind.Product, null, VMOptionVariant.XX),
        new VMOption("Experimental", "uint", "10", VMOptionKind.Experimental, null, VMOptionVariant.XX),
        new VMOption("Diagnostic", "uint", "20", VMOptionKind.Diagnostic, null, VMOptionVariant.XX),
        new VMOption("mx", null, null, VMOptionKind.Product, null, VMOptionVariant.X),
        new VMOption("add-exports", null, null, VMOptionKind.Product, null, VMOptionVariant.DASH_DASH),
        new VMOption("add-opens", null, null, VMOptionKind.Product, null, VMOptionVariant.DASH_DASH)
      )));
    }
  }
}
