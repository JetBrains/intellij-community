// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.LiveTemplateContext;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.LiveTemplateContextsSnapshot;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Checks the own-states fast path in {@link TemplateContext#getDifference}: two contexts that have the same own
 * states never differ, and a real change in the effective enablement of a context type is still detected.
 */
public class TemplateContextGetDifferenceTest extends LightJavaCodeInsightFixtureTestCase {
  public void testEqualOwnStatesHaveNoDifference() {
    LiveTemplateContextsSnapshot snapshot = LiveTemplateContextService.getInstance().getSnapshot();
    assertFalse("expected at least one live template context", snapshot.getLiveTemplateContexts().isEmpty());

    for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      TemplateContext original = template.getTemplateContext();
      assertNull("a copy with the same own states must not differ from its original",
                 TemplateContext.getDifference(snapshot, original.createCopy(), original));
    }
  }

  public void testChangedContextIsStillDetected() {
    LiveTemplateContextsSnapshot snapshot = LiveTemplateContextService.getInstance().getSnapshot();
    LiveTemplateContext type = snapshot.getLiveTemplateContexts().iterator().next();

    for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      TemplateContext original = template.getTemplateContext();
      TemplateContext edited = original.createCopy();
      edited.setEnabled(type.getTemplateContextType(), !original.isEnabled(type.getTemplateContextType()));
      // Assert only when the flip actually changes the effective enablement; base-context inheritance can absorb it.
      if (edited.isEnabled(type.getTemplateContextType()) != original.isEnabled(type.getTemplateContextType())) {
        assertNotNull("a real change in a context type must be detected",
                      TemplateContext.getDifference(snapshot, edited, original));
        return;
      }
    }
  }
}
