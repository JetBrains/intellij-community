// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunAll;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.test.VcsPlatformTest;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

public class VcsRepositoryManagerTest extends VcsPlatformTest {
  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private VcsRepositoryManager myGlobalRepositoryManager;
  private MockAbstractVcs myVcs;
  private CountDownLatch CONTINUE_MODIFY;
  private CountDownLatch READY_TO_READ;
  private static final String LOCK_ERROR_TEXT = "Possible dead lock occurred!";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    cd(projectRoot);

    myVcs = new MockAbstractVcs(myProject);
    myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    myProjectLevelVcsManager.registerVcs(myVcs);
    READY_TO_READ = new CountDownLatch(1);
    CONTINUE_MODIFY = new CountDownLatch(1);

    VcsRepositoryCreator mockCreator = createMockRepositoryCreator();
    ExtensionPoint<VcsRepositoryCreator> point = getExtensionPoint();
    point.registerExtension(mockCreator, getTestRootDisposable());

    myGlobalRepositoryManager = new VcsRepositoryManager(myProject, myProjectLevelVcsManager);
  }

  @NotNull
  private ExtensionPoint<VcsRepositoryCreator> getExtensionPoint() {
    return Extensions.getArea(myProject).getExtensionPoint(VcsRepositoryCreator.EXTENSION_POINT_NAME);
  }

  @Override
  protected void tearDown() {
    new RunAll()
      .append(() -> myProjectLevelVcsManager.unregisterVcs(myVcs))
      .append(() -> Disposer.dispose(myGlobalRepositoryManager))
      .append(() -> super.tearDown())
      .run();
  }

  public void testRepositoryInfoReadingWhileModifying() throws Exception {
    final VirtualFile repositoryFile = createExternalRepository();
    assertNotNull(myGlobalRepositoryManager.getRepositoryForRoot(repositoryFile));

    FutureTask<Repository> readExistingRepo = new FutureTask<>(() -> myGlobalRepositoryManager.getRepositoryForRoot(repositoryFile));

    FutureTask<Boolean> modifyRepositoryMapping = new FutureTask<>(() -> {
      myProjectLevelVcsManager
        .setDirectoryMappings(
          VcsUtil.addMapping(myProjectLevelVcsManager.getDirectoryMappings(), projectRoot.getPath(), myVcs.getName()));
      return !myGlobalRepositoryManager.getRepositories().isEmpty();
    });
    Thread modify = new Thread(modifyRepositoryMapping,"vcs modify");
    modify.start();

    //wait until modification starts
    assertTrue(LOCK_ERROR_TEXT, READY_TO_READ.await(1, TimeUnit.SECONDS));

    Thread read = new Thread(readExistingRepo,"vcs read");
    read.start();
    assertNotNull(readExistingRepo.get(1, TimeUnit.SECONDS));
    CONTINUE_MODIFY.countDown();
    assertTrue(modifyRepositoryMapping.get(1, TimeUnit.SECONDS));
    read.join();
    modify.join();
  }

  @NotNull
  private VirtualFile createExternalRepository() {
    cd(projectRoot);
    String externalName = "external";
    mkdir(externalName);
    projectRoot.refresh(false, true);
    final VirtualFile repositoryFile = ObjectUtils.assertNotNull(projectRoot.findChild(externalName));
    MockRepositoryImpl externalRepo = new MockRepositoryImpl(myProject, repositoryFile, myProject);
    myGlobalRepositoryManager.addExternalRepository(repositoryFile, externalRepo);
    return repositoryFile;
  }

  @NotNull
  private VcsRepositoryCreator createMockRepositoryCreator() {
    return new VcsRepositoryCreator() {
      @NotNull
      @Override
      public VcsKey getVcsKey() {
        return myVcs.getKeyInstanceMethod();
      }

      @Nullable
      @Override
      public Repository createRepositoryIfValid(@NotNull VirtualFile root, @NotNull Disposable parentDisposable) {
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
