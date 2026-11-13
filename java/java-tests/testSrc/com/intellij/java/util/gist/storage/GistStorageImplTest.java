// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.gist.storage;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.gist.storage.GistStorage;
import com.intellij.util.gist.storage.GistStorage.Gist;
import com.intellij.util.gist.storage.GistStorage.GistData;
import com.intellij.util.gist.storage.GistStorageImpl;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestApplication
public class GistStorageImplTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();

  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  public static final int DEFAULT_GIST_VERSION = 1;
  public static final int DEFAULT_GIST_STAMP = 42;

  @Test
  public void newGistHasNoData() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    assertThat(
      "Gist data is not exists since nobody puts it",
      gist.getGlobalData(file, 0).hasData(),
      is(false)
    );
  }

  @Test
  public void attemptToCreateGist_WithSameName_ButDifferentVersion_Fails() throws Throwable {
    gistOf("testGist", DEFAULT_GIST_VERSION);
    assertThrows(
      IllegalArgumentException.class,
      () -> gistOf("testGist", DEFAULT_GIST_VERSION + 1)
    );
  }

  @Test
  public void dataPutInGist_GetBackAsIs_IfGistStampIsTheSame() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    String dataToStore = "test data";
    int gistStamp = DEFAULT_GIST_STAMP;

    gist.putGlobalData(file, dataToStore, gistStamp);
    GistData<String> gistData = gist.getGlobalData(file, gistStamp);
    assertThat(
      "Gist returns data put into it",
      gistData.dataIfExists(),
      equalTo(dataToStore)
    );
  }

  @Test
  public void dataPutInGist_GetBackNothing_IfGistStampDiffers() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    String dataToStore = "test data";
    int gistStamp = DEFAULT_GIST_STAMP;

    gist.putGlobalData(file, dataToStore, gistStamp);
    GistData<String> gistData = gist.getGlobalData(file, gistStamp + 1);
    assertThat(
      "Gist data does not exist (since stamp is invalid)",
      gistData.hasData(),
      is(false)
    );
    assertThat(
      "Gist stamp returned is same as put",
      gistData.gistStamp(),
      is(gistStamp)
    );
  }


  @Test
  public void hugeDataPutInGist_GetBackAsIs_IfGistStampIsTheSame() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    String hugeDataToStore = "a".repeat(GistStorageImpl.MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 1);

    int gistStamp = DEFAULT_GIST_STAMP;

    gist.putGlobalData(file, hugeDataToStore, gistStamp);
    GistData<String> gistData = gist.getGlobalData(file, gistStamp);
    assertThat(
      "Gist returns data put into it",
      gistData.dataIfExists(),
      equalTo(hugeDataToStore)
    );
  }

  @Test
  public void hugeDataPutInGist_GetBackNothing_IfGistStampDiffers() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    String hugeDataToStore = "a".repeat(GistStorageImpl.MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 1);

    int gistStamp = DEFAULT_GIST_STAMP;
    gist.putGlobalData(file, hugeDataToStore, gistStamp);
    GistData<String> gistData = gist.getGlobalData(file, gistStamp + 1);
    assertThat(
      "Gist data does not exist (since stamp is invalid)",
      gistData.hasData(),
      is(false)
    );
    assertThat(
      "Gist stamp returned is same as put",
      gistData.gistStamp(),
      is(gistStamp)
    );
  }

  /** A FIFO blocks the open in native code until a writer comes, as a stalled disk or a virus scanner does. */
  @Test
  public void hugeDataRead_IsCancellable_WhileGistFileIsBlocked() throws Throwable {
    assumeTrue(!SystemInfo.isWindows, "mkfifo is not available on Windows");
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    String hugeDataToStore = "a".repeat(GistStorageImpl.MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + 1);
    int gistStamp = DEFAULT_GIST_STAMP;
    gist.putGlobalData(file, hugeDataToStore, gistStamp);

    Path gistFile = hugeGistFileOf(file);
    Files.delete(gistFile);
    Process mkfifo = new ProcessBuilder("mkfifo", gistFile.toString()).inheritIO().start();
    assertThat("mkfifo succeeds", mkfifo.waitFor(), is(0));

    ProgressIndicator indicator = new EmptyProgressIndicator();
    Future<?> read = AppExecutorUtil.getAppExecutorService().submit(() -> ProgressManager.getInstance().runProcess(
      () -> {
        try {
          return gist.getGlobalData(file, gistStamp);
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      },
      indicator
    ));
    try {
      TimeoutUtil.sleep(500);
      indicator.cancel();
      try {
        read.get(10, TimeUnit.SECONDS);
        fail("The read must end with a ProcessCanceledException");
      }
      catch (ExecutionException e) {
        assertThat("The read ends with a cancellation", e.getCause() instanceof ProcessCanceledException, is(true));
      }
      catch (TimeoutException e) {
        fail("The read of a blocked gist file does not react to the cancellation");
      }
    }
    finally {
      releaseFifo(gistFile);
    }
  }

  private static @NotNull Path hugeGistFileOf(@NotNull VirtualFile file) throws IOException {
    Path hugeGistsDir = FSRecords.getCacheDir().resolve("huge-gists").resolve(Long.toString(FSRecords.getCreationTimestamp()));
    String prefix = ((VirtualFileWithId)file).getId() + ".";
    try (Stream<Path> children = Files.list(hugeGistsDir)) {
      return children.filter(child -> child.getFileName().toString().startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No huge gist file for " + file + " in " + hugeGistsDir));
    }
  }

  /** Opens the FIFO for a write, so that a reader that still waits gets an EOF. */
  private static void releaseFifo(@NotNull Path fifo) throws IOException {
    Thread writer = new Thread(() -> {
      try {
        Files.newOutputStream(fifo).close();
      }
      catch (IOException ignored) {
      }
    }, "GistStorageImplTest FIFO writer");
    writer.setDaemon(true);
    writer.start();
    TimeoutUtil.sleep(200);
    Files.deleteIfExists(fifo);
  }


  @Test
  public void gistDataStoredForDifferentProjects_AreStoredSeparately() throws Throwable {
    Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
    VirtualFile file = emptyFile(randomFileName());
    int gistStamp = DEFAULT_GIST_STAMP;

    Project[] projects = generateFakeProjects(5);
    for (Project project : projects) {
      String perProjectDataToStore = project.getLocationHash();
      gist.putProjectData(project, file, perProjectDataToStore, gistStamp);
      GistData<String> gistData = gist.getProjectData(project, file, gistStamp);
      assertThat(
        "Gist data does exist",
        gistData.hasData(),
        is(true)
      );
      assertThat(
        "Gist stamp returned is same as was put",
        gistData.gistStamp(),
        is(gistStamp)
      );
      assertThat(
        "Gist data returned is same as was put",
        gistData.dataIfExists(),
        equalTo(perProjectDataToStore)
      );
    }

    //second attempt: ensure there was no interference between different project's gists:
    for (Project project : projects) {
      String perProjectDataStored = project.getLocationHash();
      GistData<String> gistData = gist.getProjectData(project, file, gistStamp);
      assertThat(
        "Gist data does exist (still)",
        gistData.hasData(),
        is(true)
      );
      assertThat(
        "Gist stamp returned is (still) the same as was put",
        gistData.gistStamp(),
        is(gistStamp)
      );
      assertThat(
        "Gist data returned is (still) the same as was put",
        gistData.dataIfExists(),
        equalTo(perProjectDataStored)
      );
    }
  }

  @Test
  public void ifGistExternalizerFail_gistIsNotWrittenAtAll() throws IOException {
    FailAbleStringExternalizer externalizer = new FailAbleStringExternalizer();

    VirtualFile file = emptyFile(randomFileName());
    int gistStamp = DEFAULT_GIST_STAMP;
    String originalData = "testData";

    Gist<String> gist = GistStorage.getInstance().newGist("testFailingGist", 1, externalizer);
    gist.putGlobalData(file, originalData, gistStamp);

    externalizer.shouldFail(true);
    try {
      gist.putGlobalData(file, "anotherData", gistStamp);
      fail("Should throw IOException");
    }
    catch (IOException e){
      //OK, expected
    }
    assertThat(
      "Gist should still contain the data before failed write -- failed write should be abandoned completely",
      gist.getGlobalData(file, gistStamp).data(),
      is(originalData)
    );
  }

  // ============================= infrastructure: ================================================ //

  private static Project[] generateFakeProjects(int projectCount) {
    Project[] projects = new Project[projectCount];
    for (int i = 0; i < projects.length; i++) {
      String locationHash = "project_" + i;
      projects[i] = (Project)Proxy.newProxyInstance(
        GistStorageImplTest.class.getClassLoader(),
        new Class[]{Project.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getLocationHash")) {
            return locationHash;
          }
          throw new UnsupportedOperationException("Only .getLocationHash() method is supported by this Project proxy");
        });
    }
    return projects;
  }

  private static final AtomicInteger filesCounter = new AtomicInteger();

  @NotNull
  private String randomFileName() {
    return codeInsightFixture.get().getTestName(true) + '.' + filesCounter.incrementAndGet();
  }


  private VirtualFile fileWithContent(@NotNull String fileName,
                                      @NotNull String fileContent) {
    return codeInsightFixture.get().addFileToProject(fileName, fileContent).getVirtualFile();
  }

  private VirtualFile emptyFile(@NotNull String fileName) {
    return fileWithContent(fileName, "");
  }

  private static @NotNull Gist<String> gistOf(@NotNull String id,
                                              int version) {
    return GistStorage.getInstance().newGist(
      id,
      version,
      EnumeratorStringDescriptor.INSTANCE
    );
  }

  private static class FailAbleStringExternalizer implements DataExternalizer<String> {
    private boolean shouldFail = false;
    @Override
    public void save(@NotNull DataOutput out,
                     String value) throws IOException {
      if(shouldFail) {
        throw new IOException("Fail to write");
      }
      IOUtil.writeUTF(out, value);
    }

    @Override
    public String read(@NotNull DataInput in) throws IOException {
      return IOUtil.readUTF(in);
    }

    public void shouldFail(boolean shouldFail) {
      this.shouldFail = shouldFail;
    }
  }
}