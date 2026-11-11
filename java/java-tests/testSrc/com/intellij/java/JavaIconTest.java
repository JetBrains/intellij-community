// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.IconManager;
import com.intellij.ui.IconTestUtil;
import com.intellij.ui.icons.CoreIconManager;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.io.IOException;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class JavaIconTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpIcons() {
    // The icon loader must not use a fake empty icon.
    IconManager.Companion.activate(new CoreIconManager());
  }

  @AfterEach
  void tearDownIcons() {
    IconManager.Companion.deactivate();
    IconLoader.INSTANCE.clearCacheInTests();
  }

  @Test
  public void testOnlyOneIconForLockedJavaClass() throws IOException {
    if (!Registry.is("ide.locked.icon.enabled", false)) return;
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.configureByText("a.java", "class A {}");
    VirtualFile file = psiFile.getVirtualFile();
    setFileWritable(file, false);
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // write actions
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
    try {
      Icon icon = ReadAction.computeBlocking(() -> {
        PsiClass psiClass = ((PsiJavaFile) psiFile).getClasses()[0];
        return AbstractPsiBasedNode.patchIcon(myFixture.getProject(), psiClass.getIcon(Iconable.ICON_FLAG_READ_STATUS), file);
      });
      List<Icon> icons = IconTestUtil.renderDeferredIcon(icon);
      assertThat(ContainerUtil.filter(icons, ic -> IconTestUtil.unwrapIcon(ic) == AllIcons.Nodes.Locked)).hasSize(1);
    }
    finally {
      setFileWritable(file, true);
    }
  }

  private void setFileWritable(VirtualFile file, boolean writable) throws IOException {
    WriteCommandAction.runWriteCommandAction(codeInsightFixture.get().getProject(),
                                             (ThrowableComputable<Void, IOException>)() -> {
                                               file.setWritable(writable);
                                               return null;
                                             });
  }
}
