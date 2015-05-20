/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryCreator;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

public class VcsRepositoryManagerTest extends UsefulTestCase {

  private ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
  private VcsRepositoryManager myGlobalRepositoryManager;
  private MockAbstractVcs myVcs;
  private Project myProject;
  private IdeaProjectTestFixture myProjectFixture;
  private CountDownLatch CONTINUE_MODIFY;
  private CountDownLatch READY_TO_READ;
  private static final String LOCK_ERROR_TEXT = "Possible dead lock occurred!";
  private VirtualFile myProjectRoot;
  private VcsRepositoryCreator myMockCreator;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public VcsRepositoryManagerTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    try {
      myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
      myProjectFixture.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }
    try {
      myProject = myProjectFixture.getProject();
      myProjectRoot = myProject.getBaseDir();
      cd(myProjectRoot);

      myVcs = new MockAbstractVcs(myProject);
      myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
      myProjectLevelVcsManager.registerVcs(myVcs);
      READY_TO_READ = new CountDownLatch(1);
      CONTINUE_MODIFY = new CountDownLatch(1);

      myMockCreator = createMockRepositoryCreator();
      ExtensionPoint<VcsRepositoryCreator> point = getExtensionPoint();
      point.registerExtension(myMockCreator);

      myGlobalRepositoryManager = new VcsRepositoryManager(myProject, myProjectLevelVcsManager);
      myGlobalRepositoryManager.initComponent();
    }
    catch (Exception e) {
      tearDown();
      throw e;
    }
  }

  @NotNull
  private ExtensionPoint<VcsRepositoryCreator> getExtensionPoint() {
    return Extensions.getArea(myProject).getExtensionPoint(VcsRepositoryCreator.EXTENSION_POINT_NAME);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myProjectLevelVcsManager != null) {
        myProjectLevelVcsManager.unregisterVcs(myVcs);
      }
      if (myMockCreator != null) {
        getExtensionPoint().unregisterExtension(myMockCreator);
      }
    }
    finally {
      // could not be Null or not created, because first catch clause in setUp throws exception with super.tearDown()
      myProjectFixture.tearDown();
      super.tearDown();
    }
  }

  public void testRepositoryInfoReadingWhileModifying() throws Exception {
    final VirtualFile repositoryFile = createExternalRepository();
    assertNotNull(myGlobalRepositoryManager.getRepositoryForRoot(repositoryFile));

    FutureTask<Repository> readExistingRepo = new FutureTask<Repository>(new Callable<Repository>() {
      @Override
      public Repository call() throws Exception {
        return myGlobalRepositoryManager.getRepositoryForRoot(repositoryFile);
      }
    });

    FutureTask<Boolean> modifyRepositoryMapping = new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        myProjectLevelVcsManager
          .setDirectoryMappings(
            VcsUtil.addMapping(myProjectLevelVcsManager.getDirectoryMappings(), myProjectRoot.getPath(), myVcs.getName()));
        return !myGlobalRepositoryManager.getRepositories().isEmpty();
      }
    });
    new Thread(modifyRepositoryMapping).start();

    //wait until modification starts
    assertTrue(LOCK_ERROR_TEXT, READY_TO_READ.await(1, TimeUnit.SECONDS));

    new Thread(readExistingRepo).start();
    assertNotNull(readExistingRepo.get(1, TimeUnit.SECONDS));
    CONTINUE_MODIFY.countDown();
    assertTrue(modifyRepositoryMapping.get(1, TimeUnit.SECONDS));
  }

  @NotNull
  private VirtualFile createExternalRepository() {
    cd(myProjectRoot);
    String externalName = "external";
    mkdir(externalName);
    myProjectRoot.refresh(false, true);
    final VirtualFile repositoryFile = ObjectUtils.assertNotNull(myProjectRoot.findChild(externalName));
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
      public Repository createRepositoryIfValid(@NotNull VirtualFile root) {
        READY_TO_READ.countDown();
        try {
          //wait until reading thread gets existing info
          if (!CONTINUE_MODIFY.await(1, TimeUnit.SECONDS)) return null;
        }
        catch (InterruptedException e) {
          return null;
        }
        return new MockRepositoryImpl(myProject, root, myProject);
      }
    };
  }
}
