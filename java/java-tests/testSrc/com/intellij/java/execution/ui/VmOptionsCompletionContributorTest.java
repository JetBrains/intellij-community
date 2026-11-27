// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.ui;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.ui.VmOptionsEditor;
import com.intellij.execution.vmOptions.JdkOptionsData;
import com.intellij.execution.vmOptions.VMOption;
import com.intellij.execution.vmOptions.VMOptionKind;
import com.intellij.execution.vmOptions.VMOptionVariant;
import com.intellij.execution.vmOptions.VMOptionsService;
import com.intellij.execution.vmOptions.VMOptionsServiceImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class VmOptionsCompletionContributorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  @Test
  public void testEmptyPrompt() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("--add-exports", "--add-opens",
                         "-agentlib:", "-agentpath:", "-D", "-da", "-disableassertions", "-disablesystemassertions","-Djava.awt.headless",
                         "-dsa", "-Duser.dir", "-Duser.home", "-Duser.name", "-ea", "-enableassertions", "-enablesystemassertions", "-esa",
                         "-javaagent:", "-Xmx", "-XX:"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
    checkPresentation(myFixture.getLookupElements()[2], "-agentlib:|null/null");
  }

  @Test
  public void testSimpleOptions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("--add-exports", "--add-opens",
                         "-agentlib:", "-agentpath:", "-D", "-da", "-disableassertions", "-disablesystemassertions", "-Djava.awt.headless",
                         "-dsa", "-Duser.dir", "-Duser.home", "-Duser.name", "-ea", "-enableassertions", "-enablesystemassertions","-esa",
                         "-javaagent:", "-Xmx", "-XX:"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
    checkPresentation(myFixture.getLookupElements()[2], "-agentlib:|null/null");
  }

  @Test
  public void testSimpleOptionsAfterDot() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-Duser.<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("-Duser.dir", "-Duser.home", "-Duser.name"), myFixture.getLookupElementStrings());
  }

  @Test
  public void testSimpleOptionsAfterDash() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-add-<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("--add-exports", "--add-opens"), myFixture.getLookupElementStrings());
  }

  @Test
  public void testSimpleOptionsBeforeAnother() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-<caret> -ea");
    myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertThat(strings).contains("-Duser.dir", "-Duser.home", "-Duser.name");
  }

  @Test
  public void testDoubleDash() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("--<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("--add-exports", "--add-opens"), myFixture.getLookupElementStrings());
    checkPresentation(myFixture.getLookupElements()[0], "--add-exports|null/null");
  }

  @Test
  public void testXX() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> element.renderElement(presentation));
    Icon icon = presentation.getIcon();
    String iconTag = icon == AllIcons.General.ShowInfos ? "[d] " :
                     icon == AllIcons.General.ShowWarning ? "[e] " : "";
    String actual = iconTag + presentation.getItemText() + "|" + presentation.getTailText() + "/" + presentation.getTypeText();
    assertEquals(expected, actual);
  }

  @Test
  public void testXXValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:Val<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("-XX:Value=");
  }

  @Test
  public void testXXDiagnostic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:Diag<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            -XX:+UnlockDiagnosticVMOptions
                            -XX:Diagnostic""");
  }

  @Test
  public void testXXExperimental() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:+Flag -XX:Exper<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            -XX:+UnlockExperimentalVMOptions
                            -XX:+Flag -XX:Experimental""");
  }

  @Test
  public void testXXExperimentalAlready() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:+Flag -XX:+UnlockExperimentalVMOptions -XX:Exper<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("-XX:+Flag -XX:+UnlockExperimentalVMOptions -XX:Experimental");
  }

  @Test
  public void testXXPlus() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:+<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("Flag", "MinusFlag"), myFixture.getLookupElementStrings());
  }

  @Test
  public void testXXAlreadyPresent() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-XX:+Flag -XX:+<caret>");
    myFixture.completeBasic();
    assertEquals(List.of("MinusFlag"), myFixture.getLookupElementStrings());
  }
  
  @Test
  public void testVmOptionDocumentation() {
    VMOption option = new VMOption("Flag", "bool", "true", VMOptionKind.Experimental, "SuperOption", VMOptionVariant.XX, null);
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

  @Test
  public void testSimpleOptionCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-Duser.di<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("-Duser.dir=<caret>");
  }

  @Test
  public void testSimpleOptionTailReplace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configure("-Duser.di<caret>=");
    myFixture.completeBasic();
    myFixture.checkResult("-Duser.dir=<caret>");
  }

  private void configure(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(PlainTextFileType.INSTANCE, text);
    ServiceContainerUtil
      .replaceService(ApplicationManager.getApplication(), VMOptionsService.class, new TestVMOptionService(), myFixture.getTestRootDisposable());
    ApplicationConfiguration configuration = new ApplicationConfiguration("Hello", myFixture.getProject());
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
      List<VMOption> vmOptionList = new ArrayList<>(VMOptionsServiceImpl.getStandardOptionList());
      vmOptionList.addAll(List.of(
          new VMOption("Flag", "bool", "true", VMOptionKind.Product, null, VMOptionVariant.XX, null),
          new VMOption("MinusFlag", "bool", "false", VMOptionKind.Product, null, VMOptionVariant.XX, null),
          new VMOption("Value", "uint", "10", VMOptionKind.Product, null, VMOptionVariant.XX, null),
          new VMOption("Experimental", "uint", "10", VMOptionKind.Experimental, null, VMOptionVariant.XX, null),
          new VMOption("Diagnostic", "uint", "20", VMOptionKind.Diagnostic, null, VMOptionVariant.XX, null),
          new VMOption("mx", null, null, VMOptionKind.Product, null, VMOptionVariant.X, null),
          new VMOption("add-exports", null, null, VMOptionKind.Product, null, VMOptionVariant.DASH_DASH, null),
          new VMOption("add-opens", null, null, VMOptionKind.Product, null, VMOptionVariant.DASH_DASH, null)
        )
      );
      return CompletableFuture.completedFuture(new JdkOptionsData(vmOptionList));
    }
  }
}
