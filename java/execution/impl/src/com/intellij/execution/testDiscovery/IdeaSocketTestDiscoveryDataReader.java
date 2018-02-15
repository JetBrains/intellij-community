// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.SocketTestDataReader;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

class IdeaSocketTestDiscoveryDataReader extends SocketTestDataReader {
  private static final Logger LOG = Logger.getInstance(IdeaSocketTestDiscoveryDataReader.class);

  @NotNull
  private final TIntObjectHashMap<String> myTestExecutionNameEnumerator;

  //test data
  private String myTestName;
  private final MultiMap<String, String> myUsedMethods = new MultiMap<>();

  IdeaSocketTestDiscoveryDataReader(@NotNull TIntObjectHashMap<String> testExecutionNameEnumerator) {
    myTestExecutionNameEnumerator = testExecutionNameEnumerator;
  }

  @NotNull
  String getTestName() {
    return myTestName;
  }

  @NotNull
  MultiMap<String, String> getUsedMethods() {
    return myUsedMethods;
  }


  @Override
  protected void processTestName(int testClassId, int testMethodId) {
    myTestName = myTestExecutionNameEnumerator.get(testClassId) + "-" + myTestExecutionNameEnumerator.get(testMethodId);
  }

  @Override
  protected void processEnumeratedName(int id, String name) {
    String previousName = myTestExecutionNameEnumerator.put(id, name);
    LOG.assertTrue(previousName == null || previousName.equals(name));
  }

  @Override
  protected void processUsedMethod(int classInternalId, int methodInternalId) {
    String className = myTestExecutionNameEnumerator.get(classInternalId);
    String methodName = myTestExecutionNameEnumerator.get(methodInternalId);
    if (className == null || methodName == null) {
      LOG.error("Inconsistent state");
      return;
    }
    myUsedMethods.putValue(className, methodName);
  }
}
