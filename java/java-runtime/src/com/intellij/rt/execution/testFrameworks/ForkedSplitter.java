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
package com.intellij.rt.execution.testFrameworks;

import com.intellij.rt.execution.junit.RepeatCount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class ForkedSplitter<T> extends ForkedByModuleSplitter {

  private T myRootDescription;

  public ForkedSplitter(String workingDirsPath, String forkMode, List newArgs) {
    super(workingDirsPath, forkMode, newArgs);
  }

  @Override
  protected int startSplitting(String[] args,
                               String configName,
                               String repeatCount) throws Exception {
    myRootDescription = createRootDescription(args, configName);
    if (myRootDescription == null) {
      return  -1;
    }
    if (myWorkingDirsPath == null || new File(myWorkingDirsPath).length() == 0) {
      final String classpath = System.getProperty("java.class.path");
      final String modulePath = System.getProperty("jdk.module.path");
      final List<String> moduleOptions = new ArrayList<String>();
      if (modulePath != null && modulePath.length() > 0) {
        moduleOptions.add("-p");
        moduleOptions.add(modulePath);
      }
      if (repeatCount != null && RepeatCount.getCount(repeatCount) != 0 && myForkMode.equals("repeat")) {
        return startChildFork(createChildArgs(myRootDescription), null, classpath, moduleOptions, repeatCount);
      }
      final List<T> children = getChildren(myRootDescription);
      final boolean forkTillMethod = myForkMode.equalsIgnoreCase("method");
      return splitChildren(children, 0, forkTillMethod, null, classpath, moduleOptions, repeatCount);
    }
    else {
      return splitPerModule(repeatCount);
    }
  }

  @Override
  protected int startPerModuleFork(String moduleName,
                                   List<String> classNames,
                                   String packageName,
                                   String workingDir,
                                   String classpath,
                                   List<String> moduleOptions,
                                   String repeatCount,
                                   int result,
                                   String filters) throws Exception {
    if (myForkMode.equals("none")) {
      final List<String> childArgs = createPerModuleArgs(packageName, workingDir, classNames, myRootDescription, filters);
      return startChildFork(childArgs, new File(workingDir), classpath, moduleOptions, repeatCount);
    }
    else {
      final List<T> children = new ArrayList<T>(getChildren(myRootDescription));
      for (Iterator<T> iterator = children.iterator(); iterator.hasNext(); ) {
        if (!classNames.contains(getTestClassName(iterator.next()))) {
          iterator.remove();
        }
      }
      final boolean forkTillMethod = myForkMode.equalsIgnoreCase("method");
      return splitChildren(children, result, forkTillMethod, new File(workingDir), classpath, moduleOptions, repeatCount);
    }
  }

  protected int splitChildren(List<T> children,
                              int result,
                              boolean forkTillMethod,
                              File workingDir,
                              String classpath,
                              List<String> moduleOptions,
                              String repeatCount) throws IOException, InterruptedException {
    for (final T child : children) {
      final List<T> childTests = getChildren(child);
      final int childResult;
      if (childTests.isEmpty() || !forkTillMethod) {
        childResult = startChildFork(createChildArgs(child), workingDir, classpath, moduleOptions, repeatCount);
      }
      else {
        childResult = splitChildren(childTests, result, forkTillMethod, workingDir, classpath, moduleOptions, repeatCount);
      }
      result = Math.min(childResult, result);
    }
    return result;
  }

  protected abstract List<String> createPerModuleArgs(String packageName,
                                                      String workingDir,
                                                      List<String> classNames,
                                                      T rootDescriptor,
                                                      String filters) throws IOException;

  protected abstract T createRootDescription(String[] args, String configName) throws Exception;

  protected abstract String getTestClassName(T child);

  protected abstract List<String> createChildArgs(T child);

  protected abstract List<T> getChildren(T child);
}
