package com.intellij.json;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.injected.InjectedLanguageTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonInjectionTest extends InjectedLanguageTestCase {
  // PY-133425
  public void testJsonFragmentInjectedInJavaString() {
    configureByFile("/injected/" + getTestName(false) + ".java");
    final List<HighlightInfo> highlightInfos = doHighlighting(HighlightSeverity.WARNING);
    assertNull(ContainerUtil.find(highlightInfos, new Condition<HighlightInfo>() {
      @Override
      public boolean value(HighlightInfo info) {
        return info.getDescription().equals(JsonBundle.message("msg.compliance.problem.illegal.property.key"));
      }
    }));
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JsonStandardComplianceInspection()};
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/json/tests/testData";
  }
}
