package com.intellij.java.injection;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class InjectedRangeMarkersTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testInjectionRangeMarkers() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Inspired by IDEA-83011
    EdtTestUtil.runInEdtAndWait(() -> registerInjection("java:(.*+)", "{", "}"));
    String text = "\\n<root>\n    <style>java:int <caret>i = 1;</style>\n</root>\n";
    myFixture.configureByText("a.xml", text);
    @Language(value="JAVA", prefix="class X")
    String expectedInjected = "{int i = 1;}";
    myFixture.doHighlighting();// Inject the stuff
    Pair<String, String> texts = EdtTestUtil.runInEdtAndGet(() -> {
      Editor editorWindow = myFixture.getEditor();
      Document documentWindow = editorWindow.getDocument();
      RangeMarker marker = documentWindow.createRangeMarker(0, documentWindow.getTextLength());
      return Pair.create(documentWindow.getText(), marker.getTextRange().substring(documentWindow.getText()));
    });
    assertEquals(expectedInjected, texts.first);
    assertEquals(expectedInjected, texts.second);
  }

  private void registerInjection(final String pattern, final String prefix, final String suffix) {
    MultiHostInjector injector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        Matcher matcher = Pattern.compile(pattern).matcher(context.getText());
        if (!matcher.find()) {
          return;
        }

        registrar.startInjecting(JavaLanguage.INSTANCE)
          .addPlace(prefix, suffix, (PsiLanguageInjectionHost)context,
                    new TextRange(matcher.start(1), matcher.end(1))).doneInjecting();
      }

      @NotNull
      @Override
      public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(XmlText.class);
      }
    };
    InjectedLanguageManager.getInstance(projectFixture.get()).registerMultiHostInjector(injector, codeInsightFixture.get().getProjectDisposable());
  }
}
