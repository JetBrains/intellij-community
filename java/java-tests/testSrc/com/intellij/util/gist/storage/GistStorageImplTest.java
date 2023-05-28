// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist.storage;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4;
import com.intellij.util.gist.storage.GistStorage.Gist;
import com.intellij.util.gist.storage.GistStorage.GistData;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 */
public class GistStorageImplTest extends LightJavaCodeInsightFixtureTestCase4 {


  public static final int DEFAULT_GIST_VERSION = 1;
  public static final int DEFAULT_GIST_STAMP = 42;

  @Test
  public void newGistHasNoData() throws Throwable {
    WriteAction.runAndWait(() -> {
      Gist<String> gist = gistOf("testGist", DEFAULT_GIST_VERSION);
      VirtualFile file = emptyFile(randomFileName());
      assertThat(
        "Gist data is not exists since nobody puts it",
        gist.getGlobalData(file, 0).hasData(),
        is(false)
      );
    });
  }

  @Test
  public void attemptToCreateGist_WithSameName_ButDifferentVersion_Fails() throws Throwable {
    gistOf("testGist", DEFAULT_GIST_VERSION);
    UsefulTestCase.assertThrows(
      IllegalArgumentException.class,
      () -> gistOf("testGist", DEFAULT_GIST_VERSION + 1)
    );
  }

  @Test
  public void dataPutInGist_GetBackAsIs_IfGistStampIsTheSame() throws Throwable {
    WriteAction.runAndWait(() -> {
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
    });
  }

  @Test
  public void dataPutInGist_GetBackNothing_IfGistStampDiffers() throws Throwable {
    WriteAction.runAndWait(() -> {
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
    });
  }


  @Test
  public void hugeDataPutInGist_GetBackAsIs_IfGistStampIsTheSame() throws Throwable {
    WriteAction.runAndWait(() -> {
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
    });
  }

  @Test
  public void hugeDataPutInGist_GetBackNothing_IfGistStampDiffers() throws Throwable {
    WriteAction.runAndWait(() -> {
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
    });
  }


  // ============================= infrastructure: ================================================ //


  private static final AtomicInteger filesCounter = new AtomicInteger();

  @NotNull
  private String randomFileName() {
    return getTestName() + '.' + filesCounter.incrementAndGet();
  }


  private VirtualFile fileWithContent(@NotNull String fileName,
                                      @NotNull String fileContent) {
    return getFixture().addFileToProject(fileName, fileContent).getVirtualFile();
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
}