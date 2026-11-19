// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.javaModulesCodeInsightFixture;
import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.multiModuleProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@NeedsIndex.Full
@TestApplication
@IndexingModeTemplate
public class JigsawCodeCompletionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/jigsaw/";

  private final TestFixture<MultiModuleProject> projectFixture = multiModuleProjectFixture(
    (String testMethodName) -> new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH + "/" + PlatformTestUtil.getTestName(testMethodName, true)),
                                                                "main", null));
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);
  private JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
  }

  @Test
  public void testNormalCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> completeBasic("Main.java", """
      public class Main {
        private void foo() {
          new MyCla<caret>
        }
      }
      """)
      .variants(new Variant("MyAClass", " org.jetbrains.a", JBColor.foreground()),
                new Variant("MyBClass", " org.jetbrains.b", JBColor.foreground()))
      .choose(new Variant("MyAClass", " org.jetbrains.a", JBColor.foreground()))
      .check("Main.java", """
        import org.jetbrains.a.MyAClass;

        public class Main {
          private void foo() {
            new MyAClass()
          }
        }
        """)
      .check("module-info.java", """
        module module.main {
          requires module.a;
          requires module.b;
        }"""));
  }

  @Test
  public void testNotExported1Completion() {
    EdtTestUtil.runInEdtAndWait(() -> completeBasic("Main.java", """
      public class Main {
        private void foo() {
          new MyCla<caret>
        }
      }
      """)
      .variants(new Variant("MyBClass", " org.jetbrains.b", JBColor.foreground()),
                new Variant("MyAClass", " org.jetbrains.a", Color.RED))
      .choose(new Variant("MyAClass", " org.jetbrains.a", Color.RED))
      .check("Main.java", """
        import org.jetbrains.a.MyAClass;

        public class Main {
          private void foo() {
            new MyAClass()
          }
        }
        """)
      .check("module-info.java", """
        module module.main {
            requires module.b;
            requires module.a;
        }"""));
  }

  @Test
  public void testNotExported2Completion() {
    EdtTestUtil.runInEdtAndWait(() -> completeBasic("Main.java", """
      public class Main {
        private void foo() {
          new MyCla<caret>
        }
      }
      """)
      .variants(new Variant("MyAClass", " org.jetbrains.a", Color.RED),
                new Variant("MyBClass", " org.jetbrains.b", Color.RED))
      .choose(new Variant("MyBClass", " org.jetbrains.b", Color.RED))
      .check("Main.java", """
        import org.jetbrains.b.MyBClass;

        public class Main {
          private void foo() {
            new MyBClass()
          }
        }
        """)
      .check("module-info.java", """
        module module.main {
            requires module.b;
        }"""));
  }

  @Test
  public void testNotExportedCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> completeBasic("Main.java", """
      public class Main {
        private void foo() {
          new MyCla<caret>
        }
      }
      """)
      .variants(new Variant("MyBClass", " org.jetbrains.b", Color.RED))
      .choose(new Variant("MyBClass", " org.jetbrains.b", Color.RED))
      .check("Main.java", """
        import org.jetbrains.b.MyBClass;

        public class Main {
          private void foo() {
            new MyBClass()
          }
        }
        """)
      .check("module-info.java", """
        module module.main {
            requires module.a;
            requires module.b;
        }"""));
  }

  @Test
  public void testCircularDependencyCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> completeBasic("Main.java", """
      public class Main {
        private void foo() {
          new MyACla<caret>
        }
      }
      """)
      .variants(new Variant("MyAClass", " org.jetbrains.a", Color.RED))
      .choose(new Variant("MyAClass", " org.jetbrains.a", Color.RED))
      .check("Main.java", """
        import org.jetbrains.a.MyAClass;

        public class Main {
          private void foo() {
            new MyAClass()
          }
        }
        """)
      .check("module-info.java", """
        module module.main {
            requires module.b;
        }"""));
  }

  private JigsawCodeCompletionTest variants(Variant @NotNull ... variants) {
    final LookupElement[] elements = myFixture.getLookupElements();
    assertEquals(variants.length, elements.length, Arrays.toString(elements));
    for (int i = 0; i < elements.length; i++) {
      final LookupElementPresentation element = NormalCompletionFixture.renderElement(elements[i]);
      assertEquals(variants[i].text(), element.getItemText());
      assertEquals(variants[i].tail(), element.getTailText());
      assertEquals(variants[i].color(), element.getItemTextForeground());
    }
    return this;
  }

  private JigsawCodeCompletionTest completeBasic(@NotNull String path, @Language("JAVA") @NotNull String text) {
    final PsiFile file = myFixture.addFileToProject(path, text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    return this;
  }

  private JigsawCodeCompletionTest choose(@NotNull Variant variant) {
    myFixture.getLookup()
      .setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals(variant.text())));
    //myFixture.type("\n");
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    return this;
  }

  private JigsawCodeCompletionTest check(@NotNull String path, @Language("JAVA") @NotNull String text) {
    Project project = myFixture.getProject();
    VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByNioFile(projectFixture.get().getSourceRoot());
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      EditorUtil.fillVirtualSpaceUntilCaret(InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.getEditor()));
      VirtualFile file = sourceRoot.findFileByRelativePath(path);
      String content = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file)).getText();
      assertEquals(text, content);
    });
    return this;
  }

  private record Variant(@NotNull String text, @NotNull String tail, @NotNull Color color) {
  }
}
