// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.intellij.testFramework.LightPlatformTestCase.getSourceRoot;

@NeedsIndex.Full
public class JigsawCodeCompletionTest extends LightFixtureCompletionTestCase {

  private MultiModuleProjectDescriptor myDescriptor;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor == null
           ? myDescriptor =
             new MultiModuleProjectDescriptor(Paths.get(getTestDataPath() + "/" + getTestName(true)), "main", null)
           : myDescriptor;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/jigsaw/";
  }

  public void testNormalCompletion() {
    completeBasic("Main.java", """
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
        }""");
  }

  public void testNotExported1Completion() {
    completeBasic("Main.java", """
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
        }""");
  }

  public void testNotExported2Completion() {
    completeBasic("Main.java", """
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
        }""");
  }

  public void testNotExportedCompletion() {
    completeBasic("Main.java", """
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
        }""");
  }

  public void testCircularDependencyCompletion() {
    completeBasic("Main.java", """
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
        }""");
  }

  private JigsawCodeCompletionTest variants(Variant @NotNull ... variants) {
    final LookupElement[] elements = myFixture.getLookupElements();
    assertEquals(Arrays.toString(elements), variants.length, elements.length);
    for (int i = 0; i < elements.length; i++) {
      final LookupElementPresentation element = NormalCompletionTestCase.renderElement(elements[i]);
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
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      EditorUtil.fillVirtualSpaceUntilCaret(InjectedLanguageEditorUtil.getTopLevelEditor(getEditor()));
      VirtualFile file = getSourceRoot().findFileByRelativePath(path);
      String content = ReadAction.compute(() -> PsiManager.getInstance(getProject()).findFile(file)).getText();
      assertEquals(text, content);
    });
    return this;
  }

  private record Variant(@NotNull String text, @NotNull String tail, @NotNull Color color) {
  }
}
