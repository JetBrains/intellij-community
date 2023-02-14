// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class CustomSeverityTest extends LightDaemonAnalyzerTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitTypeCanBeDiamondInspection(),
    };
  }

  public void testCustomSeverityLayerPriority() {
    final Project project = getProject();

    final String severityName = "X";
    final TextAttributesKey attributesKey = TextAttributesKey.createTextAttributesKey(severityName);
    final HighlightSeverity severity = new HighlightSeverity(severityName, 50);
    final var textAttributes = new SeverityRegistrar.SeverityBasedTextAttributes(
      new TextAttributes(null, Color.PINK, null, null, Font.PLAIN),
      new HighlightInfoType.HighlightInfoTypeImpl(severity, attributesKey)
    );
    final SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(project);
    registrar.registerSeverity(textAttributes, null);
    Disposer.register(getTestRootDisposable(), () -> registrar.unregisterSeverity(severity));

    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    profile.setErrorLevel(HighlightDisplayKey.find("Convert2Diamond"), HighlightDisplayLevel.find(severity), project);
    assertEquals(severity, profile.getErrorLevel(HighlightDisplayKey.find("Convert2Diamond"), null).getSeverity());

    configureFromFileText("test.java", """
      import java.util.ArrayList;
      import java.util.List;
            
      class Foo {
        public List<String> foo() {
          List<String> list = new ArrayList<String>();
          return list;
        }
      }
      """);
    final List<HighlightInfo> highlighting = ContainerUtil.filter(
      doHighlighting(),
      highlightInfo -> "Convert2Diamond".equals(highlightInfo.getInspectionToolId())
    );
    assertSize(1, highlighting);

    final HighlightInfo info = highlighting.get(0);
    assertEquals(severity, info.getSeverity());
    assertEquals(HighlighterLayer.WARNING, info.getHighlighter().getLayer());
  }
}
