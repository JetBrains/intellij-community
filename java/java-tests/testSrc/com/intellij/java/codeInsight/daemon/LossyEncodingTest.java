// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LossyEncodingTest {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lossyEncoding";

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(
    JavaTestUtil.getJavaTestDataPath(), () -> new LocalInspectionTool[]{new LossyEncodingInspection()});

  @AfterEach
  void tearDown() {
    // invokeLater() in EncodingProjectManagerImpl.reloadAllFilesUnder()
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
  }

  @Test
  public void testText() {
    doTest("Text.txt");
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    Charset ascii = StandardCharsets.US_ASCII;
    VirtualFile myVFile = fixture.getFile().getVirtualFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      EncodingProjectManager.getInstance(fixture.getProject()).setEncoding(myVFile, ascii);
      dispatchUntilVfsEventsAreProcessed(); // wait for reload requests to bubble up
    });
    assertEquals(ascii, myVFile.getCharset());
    int start = caretOffset(fixture);
    fixture.type((char)0x445);
    fixture.type((char)0x438);
    int end = caretOffset(fixture);

    Collection<HighlightInfo> infos = fixture.doHighlighting();
    assertThat(infos).hasSize(1);
    HighlightInfo info = infos.iterator().next();
    assertEquals("Unsupported characters for the charset 'US-ASCII'", info.getDescription());
    assertEquals(start, info.startOffset);
    assertEquals(end, info.endOffset);

    fixture.backspace();
    fixture.backspace();

    fixture.doDoTest(true, false);
  }

  @Test
  public void testNativeConversion() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(PropertiesFileType.INSTANCE, "a=<caret>v");
    EdtTestUtil.runInEdtAndWait(() -> {
      EncodingProjectManager.getInstance(fixture.getProject()).setNative2AsciiForPropertiesFiles(null, true);
      dispatchUntilVfsEventsAreProcessed();  //reload files
    });

    fixture.type('\\');
    fixture.type('\\');

    Collection<HighlightInfo> infos = fixture.doHighlighting();
    assertEquals(0, infos.size());
  }

  @Test
  public void testTyping() {
    doTest("Simple.xml");
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.type("US-ASCII");

    Collection<HighlightInfo> infos = fixture.doHighlighting();
    assertThat(infos).hasSize(1);
    HighlightInfo info = infos.iterator().next();

    assertEquals("Unsupported characters for the charset 'US-ASCII'", info.getDescription());
  }

  @Test
  public void testMultipleRanges() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFile(BASE_PATH + "/" + "MultipleRanges.xml");
    fixture.type("US-ASCII");

    fixture.doDoTest(true, false);
  }

  private void doTest(@NonNls String filePath) {
    daemonFixture.get().doTest(BASE_PATH + "/" + filePath, true, false);
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
  }

  /**
   * Dispatches the EDT events until the async VFS events of a charset change are applied.
   * The daemon of the test does not wait for the event processing, and a VFS change during the highlighting fails the test.
   */
  private static void dispatchUntilVfsEventsAreProcessed() {
    do {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    while (RefreshQueueImpl.isRefreshInProgress() || RefreshQueueImpl.isEventProcessingInProgress());
  }

  private static int caretOffset(DaemonAnalyzerFixture fixture) {
    return ReadAction.computeBlocking(() -> fixture.getEditor().getCaretModel().getOffset());
  }

  @Test
  public void testNativeEncoding() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      EncodingProjectManager.getInstance(fixture.getProject()).setNative2AsciiForPropertiesFiles(null, true);
      dispatchUntilVfsEventsAreProcessed();
    });
    fixture.configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    fixture.doDoTest(true, false);
  }

  @Test
  public void testDetectWrongEncoding() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    VirtualFile virtualFile = fixture.findVirtualFile(BASE_PATH + "/Win1251.txt");
    virtualFile.setCharset(StandardCharsets.UTF_8);
    fixture.configureByExistingFile(virtualFile);
    Document document = Objects.requireNonNull(ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(virtualFile)));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());

    fixture.doHighlighting();
    List<HighlightInfo> infos = fileLevelHighlights(fixture);
    assertThat(infos).hasSize(1);
    assertEquals("The file was loaded in a wrong encoding: 'UTF-8'", infos.getFirst().getDescription());
  }

  @Test
  public void testReloadInNativeEncodingWhenJvmUsesUtf8() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    PlatformTestUtil.withSystemProperty("sun.jnu.encoding", "UTF-8", () -> {
      PlatformTestUtil.withSystemProperty("sun.jnu.encoding.sys", "windows-31j", () -> {
        var charset = Charset.forName("windows-31j");
        var text = "日本語\n";
        var encodingManager = EncodingProjectManager.getInstance(fixture.getProject());
        encodingManager.setDefaultCharsetName(StandardCharsets.UTF_8.name());
        Path ioFile = fixture.createTempVirtualDir().toNioPath().resolve("Japanese.txt");
        Files.writeString(ioFile, text, charset);
        var virtualFile = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile));
        virtualFile.setCharset(StandardCharsets.UTF_8);
        fixture.configureByExistingFile(virtualFile);
        var document = Objects.requireNonNull(ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(virtualFile)));
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
        assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());
        assertNotEquals(text, document.getText());

        fixture.doHighlighting();
        var infos = fileLevelHighlights(fixture);
        assertThat(infos).hasSize(1);
        assertEquals("The file was loaded in a wrong encoding: 'UTF-8'", infos.getFirst().getDescription());
        var reloadAction = "Reload in 'windows-31j'";
        EdtTestUtil.runInEdtAndWait(() -> {
          assertNotNull(DaemonAnalyzerFixture.findIntentionAction(infos, reloadAction, fixture.getEditor(), fixture.getFile()));
          assertNotNull(DaemonAnalyzerFixture.findIntentionAction(infos, "Set project encoding to 'windows-31j'", fixture.getEditor(), fixture.getFile()));
        });

        fixture.findAndInvokeIntentionAction(infos, reloadAction);
        EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

        assertEquals(charset, virtualFile.getCharset());
        assertEquals(text, document.getText());
        assertEquals(StandardCharsets.UTF_8, encodingManager.getDefaultCharset());
        fixture.doHighlighting();
        assertThat(fileLevelHighlights(fixture)).isEmpty();
      });
    });
  }

  @Test
  public void testSurrogateUTF8() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    VirtualFile virtualFile = fixture.findVirtualFile(BASE_PATH + "/" + "surrogate.txt");
    virtualFile.setCharset(StandardCharsets.UTF_8);
    fixture.configureByExistingFile(virtualFile);
    final Document document = Objects.requireNonNull(ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(virtualFile)));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());

    assertThat(fixture.doHighlighting()).isEmpty();
  }

  @Test
  public void testInconsistentLineSeparators() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    VirtualFile virtualFile = fixture.findVirtualFile(BASE_PATH + "/" + fixture.getTestName(false) + ".txt");
    fixture.configureByExistingFile(virtualFile);
    EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
    final Document document = Objects.requireNonNull(ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(virtualFile)));
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    fixture.doHighlighting();
    assertThat(fileLevelHighlights(fixture)).isEmpty();
  }

  private static List<HighlightInfo> fileLevelHighlights(DaemonAnalyzerFixture fixture) {
    return ReadAction.computeBlocking(
      () -> ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzerEx.getInstanceEx(fixture.getProject())).getFileLevelHighlights(fixture.getProject(), fixture.getFile()));
  }
}
