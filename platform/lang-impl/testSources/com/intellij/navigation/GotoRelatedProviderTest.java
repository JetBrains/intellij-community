// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.ide.actions.GotoRelatedSymbolAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GotoRelatedProviderTest extends LightPlatformCodeInsightTestCase {
  public void testGetItemsMustBeCalledOutsideEDT() {
    ExtensionPointName<GotoRelatedProvider> name = new ExtensionPointName<>("com.intellij.gotoRelatedProvider");
    ExtensionTestUtil.addExtensions(name, List.of(new MyGotoRelatedProvider()), getTestRootDisposable());
    Document document = configureFromFileText("test.txt", "xxx");
    GotoRelatedSymbolAction action = new GotoRelatedSymbolAction();
    for (int i=0; i<document.getTextLength(); i++) {
      UIUtil.dispatchAllInvocationEvents();
      countPsiElement.set(0);
      countDataContext.set(0);
      getEditor().getCaretModel().moveToOffset(i);
      DataContext context = SimpleDataContext.builder()
        .add(CommonDataKeys.PSI_FILE, getFile())
        .add(CommonDataKeys.EDITOR, getEditor())
        .add(CommonDataKeys.PSI_ELEMENT, getFile().findElementAt(getEditor().getCaretModel().getOffset()))
        .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, new JLabel())
        .build();
      action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", context));
      assertTrue(countPsiElement.get() != 0);
      assertTrue(countDataContext.get() != 0);
    }
  }

  private static final AtomicInteger countPsiElement = new AtomicInteger();
  private static final AtomicInteger countDataContext = new AtomicInteger();
  static class MyGotoRelatedProvider extends GotoRelatedProvider {
    @Override
    public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull PsiElement psiElement) {
      countPsiElement.incrementAndGet();
      ThreadingAssertions.assertBackgroundThread();
      return List.of();
    }

    @Override
    public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
      countDataContext.incrementAndGet();
      ThreadingAssertions.assertBackgroundThread();
      return List.of();
    }
  }
}
