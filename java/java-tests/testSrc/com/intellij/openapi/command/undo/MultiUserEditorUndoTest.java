// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.ClientCopyPasteManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.client.*;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serviceContainer.ComponentManagerImpl;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.function.Predicate;

public class MultiUserEditorUndoTest extends MultiUserEditorUndoTestCase {
  private Disposable myDisposable;

  @Override
  protected String initialDocText() {
    return "test";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDisposable = Disposer.newDisposable("disposable for test clients");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testUndoSimpleCommandWithMovedRanges() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);
    ClientId bId = registerClient("B.id", myProject, myDisposable);

    typeWithFlush('1');
    typeWithFlush('2', aId);
    typeWithFlush('3', bId);
    typeWithFlush('4');
    typeWithFlush('5', aId);
    typeWithFlush('6', bId);
    checkEditorText("123456test", getFirstEditor());

    // Now shared undo stack is [(old) 1, 2, 3, 4, 5, 6 (recent)]
    // Undo in "incorrect" order
    undoFirstEditor();
    checkEditorText("12356test", getFirstEditor());
    undoFirstEditor();
    checkEditorText("2356test", getFirstEditor());

    undoFirstEditor(aId);
    checkEditorText("236test", getFirstEditor());
    undoFirstEditor(aId);
    checkEditorText("36test", getFirstEditor());
  }

  public void testRedoSimpleCommandWithMovedRanges() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);
    ClientId bId = registerClient("B.id", myProject, myDisposable);

    typeWithFlush('1');
    typeWithFlush('2', aId);
    typeWithFlush('3', bId);
    typeWithFlush('4');
    typeWithFlush('5', aId);
    typeWithFlush('6', bId);
    checkEditorText("123456test", getFirstEditor());

    // Undo in "correct" order
    undoFirstEditor(bId);
    undoFirstEditor(aId);
    undoFirstEditor();
    undoFirstEditor(bId);
    undoFirstEditor(aId);
    undoFirstEditor();
    checkEditorText("test", getFirstEditor());

    // Now shared redo stack is [(old) 6, 5, 4, 3, 2, 1 (recent)]
    // Redo in "incorrect" order
    redoFirstEditor(bId);
    checkEditorText("3test", getFirstEditor());
    redoFirstEditor(bId);
    checkEditorText("36test", getFirstEditor());

    redoFirstEditor(aId);
    checkEditorText("236test", getFirstEditor());
    redoFirstEditor(aId);
    checkEditorText("2356test", getFirstEditor());
  }

  public void testUndoCompositeCommandWithMovedRanges() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);
    ClientId bId = registerClient("B.id", myProject, myDisposable);

    typeWithoutFlush('1');
    typeWithoutFlush('2', aId);
    typeWithoutFlush('3', bId);
    typeWithoutFlush('4');
    typeWithoutFlush('5', aId);
    typeWithoutFlush('6', bId);
    flushCommandMergers(ClientId.getLocalId(), aId, bId);
    checkEditorText("123456test", getFirstEditor());

    // Now shared undo stack is [(old) 1, 2, 3, 4, 5, 6 (recent)]
    // Undo in "incorrect" order
    undoFirstEditor();
    checkEditorText("2356test", getFirstEditor());

    undoFirstEditor(aId);
    checkEditorText("36test", getFirstEditor());
  }

  public void testRedoCompositeCommandWithMovedRanges() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);
    ClientId bId = registerClient("B.id", myProject, myDisposable);

    typeWithoutFlush('1');
    typeWithoutFlush('2', aId);
    typeWithoutFlush('3', bId);
    typeWithoutFlush('4');
    typeWithoutFlush('5', aId);
    typeWithoutFlush('6', bId);
    flushCommandMergers(ClientId.getLocalId(), aId, bId);
    checkEditorText("123456test", getFirstEditor());

    // Undo in "correct" order
    undoFirstEditor(bId);
    undoFirstEditor(aId);
    while (isUndoInFirstEditorAvailable()) {  // May require extra undo due to moved caret
      undoFirstEditor();
    }
    checkEditorText("test", getFirstEditor());

    // Now shared redo stack is [(old) 6, 3, 5, 2, 4, 1 (recent)]
    // Redo in "incorrect" order
    redoFirstEditor(bId);
    checkEditorText("36test", getFirstEditor());

    redoFirstEditor(aId);
    checkEditorText("2356test", getFirstEditor());
  }

  public void testUndoSimpleCommandWithConflicts() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);

    typeWithoutFlush('1', aId);
    typeWithoutFlush('2', aId);
    flushCommandMergers(aId);
    checkEditorText("12test", getFirstEditor());

    backspace(getFirstEditor());
    checkEditorText("1test", getFirstEditor());

    try {
      undoFirstEditor(aId);
      fail("Exception expected");
    } catch (Exception ignored) {
    }
    checkEditorText("1test", getFirstEditor());

    undoFirstEditor();
    checkEditorText("12test", getFirstEditor());

    undoFirstEditor(aId);
    checkEditorText("test", getFirstEditor());
  }

  public void testRedoSimpleCommandWithConflicts() {
    ClientId aId = registerClient("A.id", myProject, myDisposable);
    ClientId bId = registerClient("B.id", myProject, myDisposable);

    getFirstEditor().getSelectionModel().setSelection(0, 4);
    backspace(getFirstEditor());
    checkEditorText("", getFirstEditor());

    undoFirstEditor();
    checkEditorText("test", getFirstEditor());

    runWithCaretStatePreserved(getFirstEditor(), () -> {  // Keep caret state so no extra redo is needed
      setCaretState(getFirstEditor(), 2, 2, 2);
      typeWithFlush('1', aId);
      checkEditorText("te1st", getFirstEditor());
    });

    checkEditorText("te1st", getFirstEditor());
    try {
      redoFirstEditor();
      fail("Exception expected");
    } catch (Exception ignored) {
    }
    checkEditorText("te1st", getFirstEditor());

    runWithCaretStatePreserved(getFirstEditor(), () -> {
      setCaretState(getFirstEditor(), 2, 2, 3);
      backspace(getFirstEditor(), bId);
      checkEditorText("test", getFirstEditor());
    });

    redoFirstEditor();
    checkEditorText("", getFirstEditor());
  }

  private static void runWithCaretStatePreserved(@NotNull Editor editor, @NotNull Runnable runnable) {
    int offset = editor.getCaretModel().getOffset();
    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();
    runnable.run();
    setCaretState(editor, offset, selectionStart, selectionEnd);
  }

  private static void setCaretState(@NotNull Editor editor, int offset, int selectionStart, int selectionEnd) {
    editor.getCaretModel().moveToOffset(offset);
    editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
  }

  private boolean isUndoInFirstEditorAvailable() {
    return myManager.isUndoAvailable(getFileEditor(getFirstEditor()));
  }

  private void flushCommandMergers(ClientId @NotNull ... clientIds) {
    for (ClientId clientId : clientIds) {
      try (AccessToken ignored = ClientId.withClientId(clientId)) {
        myManager.flushCurrentCommandMerger();
      }
    }
  }

  private void backspace(@NotNull Editor editor, @NotNull ClientId clientId) {
    try (AccessToken ignored = ClientId.withClientId(clientId)) {
      backspace(editor);
    }
  }

  private void undoFirstEditor(@NotNull ClientId clientId) {
    try (AccessToken ignored = ClientId.withClientId(clientId)) {
      undoFirstEditor();
    }
  }

  private void redoFirstEditor(@NotNull ClientId clientId) {
    try (AccessToken ignored = ClientId.withClientId(clientId)) {
      redoFirstEditor();
    }
  }

  private void typeWithoutFlush(char c, @NotNull ClientId clientId) {
    try (AccessToken ignored = ClientId.withClientId(clientId)) {
      typeWithoutFlush(c);
    }
  }

  private void typeWithoutFlush(char c) {
    executeCommand("", "Sample group ID", () -> typeInChar(c));
  }

  private void typeWithFlush(char c, @NotNull ClientId clientId) {
    try (AccessToken ignored = ClientId.withClientId(clientId)) {
      typeWithFlush(c);
    }
  }

  protected void typeWithFlush(char c) {
    executeCommand(() -> typeInChar(c));
  }

  private static @NotNull ClientId registerClient(@NotNull String id, @NotNull Project project, @NotNull Disposable disposable) {
    ClientId clientId = new ClientId(id);
    registerProjectSession(clientId, project, disposable);
    registerAppSession(clientId, disposable);
    return clientId;
  }

  private static void registerProjectSession(@NotNull ClientId clientId, @NotNull Project project, @NotNull Disposable disposable) {
    ClientProjectSessionImpl clientProjectSession = new ClientProjectSessionImpl(clientId, ClientType.GUEST, (ProjectImpl)project);
    registerSession(clientProjectSession, project, disposable);
  }

  private static void registerAppSession(@NotNull ClientId clientId, @NotNull Disposable disposable) {
    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    ClientAppSessionImpl clientAppSession = new ClientAppSessionImpl(clientId, ClientType.GUEST, application) {
      @NotNull
      @Override
      public @NlsSafe String getName() {
        return clientId.getValue();
      }
    };
    registerSession(clientAppSession, application, disposable);
    PluginDescriptor descriptor = ComponentManagerImpl.fakeCorePluginDescriptor;
    clientAppSession.registerServiceInstance(ClientCopyPasteManager.class, new MockCopyPasteManager(), descriptor);
  }

  private static void registerSession(@NotNull ClientSessionImpl session,
                                      @NotNull ComponentManager componentManager,
                                      @NotNull Disposable disposable) {
    ClientSessionsManager<ClientSession> sessionsManager = getClientSessionsManager(componentManager);
    sessionsManager.registerSession(disposable, session);
    session.registerComponents();
    BuildersKt.launch(GlobalScope.INSTANCE, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT, (scope, continuation) -> {
      session.preloadServices(scope);
      return Unit.INSTANCE;
    });
  }

  @SuppressWarnings("unchecked")
  private static @NotNull ClientSessionsManager<ClientSession> getClientSessionsManager(@NotNull ComponentManager componentManager) {
    return componentManager.getService(ClientSessionsManager.class);
  }

  private static class MockCopyPasteManager implements ClientCopyPasteManager {
    @Override
    public boolean areDataFlavorsAvailable(DataFlavor @NotNull... flavors) {
      return false;
    }

    @Override
    public void setContents(@NotNull Transferable content) {

    }

    @Override
    public boolean removeIf(@NotNull Predicate<? super Transferable> predicate) {
      return false;
    }

    @Override
    public void addContentChangedListener(CopyPasteManager.@NotNull ContentChangedListener listener) {

    }

    @Override
    public void addContentChangedListener(CopyPasteManager.@NotNull ContentChangedListener listener, @NotNull Disposable parentDisposable) {

    }

    @Override
    public void removeContentChangedListener(CopyPasteManager.@NotNull ContentChangedListener listener) {

    }

    @Nullable
    @Override
    public Transferable getContents() {
      return null;
    }

    @Nullable
    @Override
    public <T> T getContents(@NotNull DataFlavor flavor) {
      return null;
    }

    @Override
    public void stopKillRings() {

    }

    @Override
    public void stopKillRings(@NotNull Document document) {

    }

    @NotNull
    @Override
    public Transferable @NotNull [] getAllContents() {
      return new Transferable[0];
    }

    @Override
    public void removeContent(@Nullable Transferable t) {

    }

    @Override
    public void moveContentToStackTop(@Nullable Transferable t) {

    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {

    }

    @Override
    public boolean isSystemSelectionSupported() {
      return false;
    }

    @Override
    public @Nullable Transferable getSystemSelectionContents() {
      return null;
    }

    @Override
    public void setSystemSelectionContents(@NotNull Transferable content) {

    }
  }
}
