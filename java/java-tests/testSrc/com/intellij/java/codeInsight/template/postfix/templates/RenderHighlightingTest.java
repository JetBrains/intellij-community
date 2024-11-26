// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import javax.swing.*;
import java.awt.*;

import static com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor.setShowTemplatesInTests;

public class RenderHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    setShowTemplatesInTests(true, getTestRootDisposable());
    myFixture.configureByText("a.java", """
      import java.util.List;
      class A {
        void m(List<String> list) {
          list.toS<caret>
        }
      }""");
    LookupElement[] elements = myFixture.completeBasic();
    ReadAction.run(() -> {
      JList<LookupElement> list = getLookup().getList();
      for (LookupElement item : getLookup().getItems()) {
        if (!item.getLookupString().equals(".toSet")) continue;
        if (list.getCellRenderer() instanceof LookupCellRenderer lookupCellRenderer) {
          Component component = list.getCellRenderer().getListCellRendererComponent(list, item, 0, false, false);
          System.out.println(component);
        }
      }
    });
  }


  private LookupImpl getLookup() {
    return (LookupImpl)myFixture.getLookup();
  }
}
