// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Tests the auto-make trigger decision of {@link BuildManager}.
 * The VFS listener itself is off in unit-test mode, so the tests call the decision method directly.
 */
public class BuildManagerAutoMakeTriggerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testContentFileChangeTriggersMake() {
    var file = addContentFile("A.java", "class A {}");
    assertTrue(BuildManager.shouldTriggerMake(getProject(), List.of(change(file))));
  }

  public void testFileOutsideContentDoesNotTriggerMake() {
    var outside = new LightVirtualFile("B.txt", "x");
    assertFalse(BuildManager.shouldTriggerMake(getProject(), List.of(change(outside))));
  }

  public void testGeneratedSourceDoesNotTriggerMake() {
    var file = addContentFile("Gen.java", "class Gen {}");
    var filter = new RecordingFilter(_ -> true);
    ExtensionTestUtil.maskExtensions(GeneratedSourcesFilter.EP_NAME, List.of(filter), getTestRootDisposable());

    assertFalse(BuildManager.shouldTriggerMake(getProject(), List.of(change(file))));
    assertEquals(List.of(file), filter.seenFiles);
  }

  public void testProjectFileDoesNotTriggerMakeAndSkipsFilters() {
    var workspaceFile = addContentFile(".idea/workspace.xml", "<project/>");
    var filter = new RecordingFilter(_ -> true);
    ExtensionTestUtil.maskExtensions(GeneratedSourcesFilter.EP_NAME, List.of(filter), getTestRootDisposable());

    assertFalse(BuildManager.shouldTriggerMake(getProject(), List.of(change(workspaceFile))));
    assertEmpty(filter.seenFiles);
  }

  public void testDeletedFileTriggersMakeWithoutProject() throws IOException {
    var file = addContentFile("Deleted.java", "class Deleted {}");
    WriteAction.run(() -> file.delete(this));
    assertFalse(file.isValid());

    assertTrue(BuildManager.shouldTriggerMake(null, List.of(new VFileDeleteEvent(null, file))));
  }

  public void testMissingOrInvalidProjectDoesNotTriggerMake() {
    var file = addContentFile("A.java", "class A {}");
    var events = List.of(change(file));

    assertFalse(BuildManager.shouldTriggerMake(null, events));
    assertFalse(BuildManager.shouldTriggerMake(ProjectManager.getInstance().getDefaultProject(), events));
  }

  public void testPendingWriteActionCancelsAndRestartsTheDecision() throws Exception {
    var file = addContentFile("A.java", "class A {}");
    var attempts = new AtomicInteger();
    var firstAttemptStarted = new CountDownLatch(1);
    var filter = new RecordingFilter(_ -> {
      if (attempts.incrementAndGet() == 1) {
        firstAttemptStarted.countDown();
        // wait for the write action to cancel this read action
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
          ProgressManager.checkCanceled();
          TimeoutUtil.sleep(1);
        }
        fail("the pending write action did not cancel the read action");
      }
      return false;
    });
    ExtensionTestUtil.maskExtensions(GeneratedSourcesFilter.EP_NAME, List.of(filter), getTestRootDisposable());

    var events = List.of(change(file));
    var project = getProject();
    var result = ApplicationManager.getApplication().executeOnPooledThread(
      () -> ReadAction.nonBlocking(() -> BuildManager.shouldTriggerMake(project, events)).executeSynchronously()
    );

    assertTrue(firstAttemptStarted.await(30, TimeUnit.SECONDS));
    WriteAction.run(() -> { });

    assertTrue(result.get(30, TimeUnit.SECONDS));
    assertEquals(2, attempts.get());
    assertEquals(List.of(file, file), filter.seenFiles);
  }

  private @NotNull VirtualFile addContentFile(@NotNull String relativePath, @NotNull String text) {
    return myFixture.addFileToProject(relativePath, text).getVirtualFile();
  }

  private static @NotNull VFileEvent change(@NotNull VirtualFile file) {
    return new VFileContentChangeEvent(null, file, 0, 1);
  }

  private static final class RecordingFilter extends GeneratedSourcesFilter {
    private final Predicate<VirtualFile> myIsGenerated;
    final List<VirtualFile> seenFiles = new ArrayList<>();

    RecordingFilter(@NotNull Predicate<VirtualFile> isGenerated) {
      myIsGenerated = isGenerated;
    }

    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      synchronized (seenFiles) {
        seenFiles.add(file);
      }
      return myIsGenerated.test(file);
    }
  }
}
