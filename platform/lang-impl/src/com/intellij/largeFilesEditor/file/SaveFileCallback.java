// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.file;

public interface SaveFileCallback {

  void tellSavingFileWasCompleteSuccessfully();

  void tellSavingFileWasCorrupted(String messageToUser);

  void tellSavingProgress(int progress);

  void tellSavingFileWasCanceledAndEverythingWasReestablished();
}
