package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JigsawCopyPasteTest extends LightJavaCodeInsightFixtureTestCase {
  private MultiModuleProjectDescriptor myDescriptor;
  private int myAddImportsOld;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor == null
           ? myDescriptor =
             new MultiModuleProjectDescriptor(Paths.get(getTestDataPath()), "main", null)
           : myDescriptor;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = myAddImportsOld;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/jigsawCopyPaste";
  }

  public void testSimple() {
    doTest("Set<Api> res = UtilA.get();",
           List.of("org.jetbrains.jigsaw.sub.module.b.Api",
                   "org.jetbrains.jigsaw.sub.module.a.UtilA",
                   "java.util.Set"), """
             module main {
                 requires sub.module.b;
                 requires sub.module.a;
             }""");
  }

  public void testNoModule() throws IOException {
    doTest("Class<?> res = NoUtil.class;",
           List.of("org.jetbrains.jigsaw.no.module"), """
             module main {
             }""");
  }

  private void doTest(@NotNull String fragment, @NotNull List<String> imports, @Language("java") @NotNull String expected) {
    // prepare data
    StringBuilder text = new StringBuilder();
    imports.forEach(i -> text.append("import ").append(i).append(";\n"));
    text.append("""
                  public class A {
                    public static void main(String[] args) {
                      <selection>""").append(fragment).append("<caret></selection>\n")
      .append("  }\n")
      .append("}");

    // copy
    myFixture.configureByText(JavaFileType.INSTANCE, text.toString());
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);

    // paste
    Path dstPath = getModule().getModuleNioFile().getParent().resolve("src/org/jetbrains/jigsaw/copypaste/Main.java");
    VirtualFile dst = VirtualFileManager.getInstance().findFileByNioPath(dstPath);
    myFixture.configureFromExistingVirtualFile(dst);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);

    // check module-info.java
    Path beforePath = getModule().getModuleNioFile().getParent().resolve("src/module-info.java");
    VirtualFile actual = VirtualFileManager.getInstance().findFileByNioPath(beforePath);

    assertEquals(expected, PsiManager.getInstance(getProject()).findFile(actual).getText());
  }
}
