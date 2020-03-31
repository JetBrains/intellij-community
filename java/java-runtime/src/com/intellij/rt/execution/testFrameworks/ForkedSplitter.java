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

public abstract class ForkedSplitter extends ForkedByModuleSplitter {

  private Object myRootDescription;

  public ForkedSplitter(String workingDirsPath, String forkMode, List newArgs) {
    super(workingDirsPath, forkMode, newArgs);
  }

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
      final List moduleOptions = new ArrayList();
      if (modulePath != null && modulePath.length() > 0) {
        moduleOptions.add("-p");
        moduleOptions.add(modulePath);
      }
      if (repeatCount != null && RepeatCount.getCount(repeatCount) != 0 && myForkMode.equals("repeat")) {
        return startChildFork(createChildArgs(myRootDescription), null, classpath, moduleOptions, repeatCount);
      }
      final List children = getChildren(myRootDescription);
      final boolean forkTillMethod = myForkMode.equalsIgnoreCase("method");
      return splitChildren(children, 0, forkTillMethod, null, classpath, moduleOptions, repeatCount);
    }
    else {
      return splitPerModule(repeatCount);
    }
  }

  protected int startPerModuleFork(String moduleName,
                                   List classNames,
                                   String packageName,
                                   String workingDir,
                                   String classpath,
                                   List moduleOptions,
                                   String repeatCount,
                                   int result,
                                   String filters) throws Exception {
    if (myForkMode.equals("none")) {
      final List childArgs = createPerModuleArgs(packageName, workingDir, classNames, myRootDescription, filters);
      return startChildFork(childArgs, new File(workingDir), classpath, moduleOptions, repeatCount);
    }
    else {
      final List children = new ArrayList(getChildren(myRootDescription));
      for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
        if (!classNames.contains(getTestClassName(iterator.next()))) {
          iterator.remove();
        }
      }
      final boolean forkTillMethod = myForkMode.equalsIgnoreCase("method");
      return splitChildren(children, result, forkTillMethod, new File(workingDir), classpath, moduleOptions, repeatCount);
    }
  }

  protected int splitChildren(List children,
                              int result,
                              boolean forkTillMethod,
                              File workingDir,
                              String classpath,
                              List moduleOptions,
                              String repeatCount) throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = getChildren(child);
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

  protected abstract List createPerModuleArgs(String packageName,
                                              String workingDir,
                                              List classNames,
                                              Object rootDescriptor,
                                              String filters) throws IOException;

  protected abstract Object createRootDescription(String[] args, String configName) throws Exception;

  protected abstract String getTestClassName(Object child);

  protected abstract List createChildArgs(Object child);

  protected abstract List getChildren(Object child);
}
