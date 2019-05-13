package com.intellij.json;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.extensions.AreaPicoContainer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.util.containers.Predicate;
import com.jetbrains.jsonSchema.JsonSchemaTestProvider;
import com.jetbrains.jsonSchema.JsonSchemaTestServiceImpl;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerTest extends JsonTestCase {

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.checkHighlighting(true, false, true);
  }

  public void testEscapeAwareness() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      JsonLanguage.INSTANCE);
  }

  public void testWithSchema() {
    PsiFile[] files = myFixture.configureByFiles(getTestName(false) + ".json", "Schema.json");
    JsonSchemaTestServiceImpl.setProvider(new JsonSchemaTestProvider(files[1].getVirtualFile(),
                                                                     getAvailabilityPredicate()));
    AreaPicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
    String key = JsonSchemaService.class.getName();
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, JsonSchemaTestServiceImpl.class);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        JsonSchemaTestServiceImpl.setProvider(null);
      }
    });
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.checkHighlighting(true, false, true);
  }

  // WEB-31894 EA-117068
  public void testAfterModificationOfStringLiteralWithEscaping() {
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.checkHighlighting();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.doHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/spellchecker";
  }
}
