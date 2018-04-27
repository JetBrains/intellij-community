// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.api.TestDiscoveryProtocolReader;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class IdeaTestDiscoveryProtocolReader implements TestDiscoveryProtocolReader, TestDiscoveryProtocolReader.NameEnumeratorReader {
  private static final Logger LOG = Logger.getInstance(IdeaTestDiscoveryProtocolReader.class);

  @NotNull
  private final TIntObjectHashMap<String> myTestExecutionNameEnumerator = new TIntObjectHashMap<>();
  @NotNull
  private final TestDiscoveryIndex myIndex;
  private final String myModuleName;
  private final byte myFrameworkId;

  IdeaTestDiscoveryProtocolReader(@NotNull TestDiscoveryIndex index,
                                  @Nullable String moduleName,
                                  byte frameworkId) {
    myIndex = index;
    myModuleName = moduleName;
    myFrameworkId = frameworkId;
  }

  @Override
  public void testDiscoveryDataProcessingStarted(int version) {

  }

  @Override
  public void testDiscoveryDataProcessingFinished() {

  }

  @Override
  public MetadataReader createMetadataReader() {
    return new MetadataReader() {
      @Override
      public void processMetadataEntry(String k, String v) {
        // do nothing
      }
    };
  }

  @Override
  public ClassMetadataReader createClassMetadataReader() {
    return new ClassMetadataReader() {
      @Override
      public void classStarted(int i) {

      }

      @Override
      public void file(int i) {

      }

      @Override
      public void method(int i, byte[] bytes) {

      }

      @Override
      public void classFinished(int i) {

      }

      @Override
      public void finished() {

      }
    };
  }

  @Override
  public NameEnumeratorReader createNameEnumeratorReader() {
    return this;
  }

  @Override
  public TestDataReader createTestDataReader(int testClassId, int testMethodId) {
    return new TestDataReader() {
      private final String myTestClassName = myTestExecutionNameEnumerator.get(testClassId);
      private final String myTestMethodName = myTestExecutionNameEnumerator.get(testMethodId);
      private final MultiMap<String, String> myUsedMethods = new MultiMap<>();
      private int myCurrentClassId;

      @Override
      public void classProcessingStarted(int classId) {
        myCurrentClassId = classId;
      }

      @Override
      public void processUsedMethod(int methodId) {
        String className = myTestExecutionNameEnumerator.get(myCurrentClassId);
        String methodName = myTestExecutionNameEnumerator.get(methodId);
        if (className == null || methodName == null) {
          LOG.error("Inconsistent state");
          return;
        }
        myUsedMethods.putValue(className, methodName);

      }

      @Override
      public void classProcessingFinished(int classId) {
        myCurrentClassId = -1;
      }

      @Override
      public void testDataProcessed() {
        myIndex.updateTestData(myTestClassName, myTestMethodName, myUsedMethods, myModuleName, myFrameworkId);
      }
    };
  }

  @Override
  public void debug(String message) {
    LOG.debug(message);
  }

  @Override
  public void error(String message) {
    LOG.error(message);
  }

  @Override
  public void error(Exception exception) {
    LOG.error(exception);
  }

  @Override
  public void enumerate(String name, int id) {
    String previousName = myTestExecutionNameEnumerator.put(id, name);
    LOG.assertTrue(previousName == null || previousName.equals(name));
  }
}
