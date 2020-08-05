// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.vcs.test.VcsPlatformTest;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

public class VcsRepositoryManagerTest extends VcsPlatformTest {
  private MockAbstractVcs myVcs;
  private CountDownLatch CONTINUE_MODIFY;
  private CountDownLatch READY_TO_READ;
  private static final String LOCK_ERROR_TEXT = "Possible dead lock occurred!";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    cd(getProjectRoot());

    myVcs = new MockAbstractVcs(myProject);
    vcsManager.registerVcs(myVcs);
    READY_TO_READ = new CountDownLatch(1);
    CONTINUE_MODIFY = new CountDownLatch(1);

    VcsRepositoryCreator mockCreator = createMockRepositoryCreator();
    ExtensionTestUtil.maskExtensions(VcsRepositoryManager.EP_NAME, Collections.singletonList(mockCreator), getTestRootDisposable(), false);
  }

  public void testRepositoryInfoReadingWhileModifying() throws Exception {
    VirtualFile repositoryFile = createExternalRepository();
    VcsRepositoryManager repositoryManager = VcsRepositoryManager.getInstance(myProject);
    assertNotNull(repositoryManager.getRepositoryForRoot(repositoryFile));

    FutureTask<Repository> readExistingRepo = new FutureTask<>(() -> repositoryManager.getRepositoryForRoot(repositoryFile));

    FutureTask<Boolean> modifyRepositoryMapping = new FutureTask<>(() -> {
      vcsManager.setDirectoryMappings(VcsUtil.addMapping(vcsManager.getDirectoryMappings(), getProjectRoot().getPath(), myVcs.getName()));
      repositoryManager.waitForAsyncTaskCompletion();
      return !repositoryManager.getRepositories().isEmpty();
    });
    Future<?> modify = ApplicationManager.getApplication().executeOnPooledThread(modifyRepositoryMapping);

    //wait until modification starts
    assertTrue(LOCK_ERROR_TEXT, READY_TO_READ.await(1, TimeUnit.SECONDS));

    Future<?> read = ApplicationManager.getApplication().executeOnPooledThread(readExistingRepo);
    assertNotNull(readExistingRepo.get(1, TimeUnit.SECONDS));
    CONTINUE_MODIFY.countDown();
    assertTrue(modifyRepositoryMapping.get(1, TimeUnit.SECONDS));
    read.get();
    modify.get();
  }

  private @NotNull VirtualFile createExternalRepository() {
    cd(getProjectRoot());
    String externalName = "external";
    mkdir(externalName);
    getProjectRoot().refresh(false, true);
    final VirtualFile repositoryFile = Objects.requireNonNull(this.getProjectRoot().findChild(externalName));
    MockRepositoryImpl externalRepo = new MockRepositoryImpl(myProject, repositoryFile, myProject);
    VcsRepositoryManager.getInstance(myProject).addExternalRepository(repositoryFile, externalRepo);
    return repositoryFile;
  }

  private @NotNull VcsRepositoryCreator createMockRepositoryCreator() {
    return new VcsRepositoryCreator() {
      @Override
      public @NotNull VcsKey getVcsKey() {
        return myVcs.getKeyInstanceMethod();
      }

      @Override
      public @Nullable Repository createRepositoryIfValid(@NotNull Project project, @NotNull VirtualFile root, @NotNull Disposable parentDisposable) {
        READY_TO_READ.countDown();
        try {
          //wait until reading thread gets existing info
          if (!CONTINUE_MODIFY.await(1, TimeUnit.SECONDS)) return null;
        }
        catch (InterruptedException e) {
          return null;
        }
        return new MockRepositoryImpl(myProject, root, parentDisposable);
      }
    };
  }
}
