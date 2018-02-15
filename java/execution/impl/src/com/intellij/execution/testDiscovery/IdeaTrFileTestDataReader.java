// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.rt.coverage.data.SingleTrFileReader;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

class IdeaTrFileTestDataReader extends SingleTrFileReader {
  private String myCurrentClassName;
  private MultiMap<String, String> myUsedMethods = new MultiMap<>();

  private final TestDiscoveryIndex myIndex;
  private final String myModuleName;
  private final String myFrameworkPrefix;

  public IdeaTrFileTestDataReader(@NotNull File file,
                                  @NotNull TestDiscoveryIndex index,
                                  @NotNull String moduleName,
                                  @NotNull String frameworkPrefix) {
    super(file);
    myIndex = index;
    myModuleName = moduleName;
    myFrameworkPrefix = frameworkPrefix;
  }

  @Override
  protected void testProcessingFinished(String testName) {
    // flush
    try {
      int separatorIndex = testName.lastIndexOf('.');
      testName = testName.substring(0, separatorIndex) + "-" + testName.substring(separatorIndex + 1);
      myIndex.updateFromData(testName, myUsedMethods, myModuleName, myFrameworkPrefix);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myUsedMethods.clear();
  }

  @Override
  protected void classProcessingFinished(String className) {
    myCurrentClassName = null;
  }

  @Override
  protected void processMethodName(String methodName) {
    myUsedMethods.putValue(myCurrentClassName, methodName);
  }

  @Override
  protected void classProcessingStarted(String className) {
    myCurrentClassName = className;
  }
}
