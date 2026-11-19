package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.javaModulesCodeInsightFixture;
import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.multiModuleProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JigsawCopyPasteTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/jigsawCopyPaste";

  private final TestFixture<MultiModuleProject> projectFixture =
    multiModuleProjectFixture(() -> new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH), "main", null));
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);
  private int myAddImportsOld;

  @BeforeEach
  void setUpSettings() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE;
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @AfterEach
  void tearDownSettings() {
    CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = myAddImportsOld;
  }

  @Test
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

  @Test
  public void testNoModule() {
    doTest("Class<?> res = NoUtil.class;",
           List.of("org.jetbrains.jigsaw.no.module"), """
             module main {
             }""");
  }

  private void doTest(@NotNull String fragment, @NotNull List<String> imports, @Language("java") @NotNull String expected) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    MultiModuleProject project = projectFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY));

    // paste
    Path dstPath = project.getModule().getModuleNioFile().getParent().resolve("src/org/jetbrains/jigsaw/copypaste/Main.java");
    VirtualFile dst = VirtualFileManager.getInstance().findFileByNioPath(dstPath);
    myFixture.configureFromExistingVirtualFile(dst);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE));

    // check module-info.java
    Path beforePath = project.getModule().getModuleNioFile().getParent().resolve("src/module-info.java");
    VirtualFile actual = VirtualFileManager.getInstance().findFileByNioPath(beforePath);

    assertEquals(expected, ReadAction.computeBlocking(() -> PsiManager.getInstance(project.getProject()).findFile(actual).getText()));
  }
}
