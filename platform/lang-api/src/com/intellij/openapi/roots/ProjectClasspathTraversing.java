/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.SmartList;

import java.util.List;

public class ProjectClasspathTraversing {
  public static final AddModuleOutput ALL_OUTPUTS = new AddModuleOutput(true);
  public static final AddModuleOutput GENERAL_OUTPUT = new AddModuleOutput(false);

  /**
   * @deprecated use <code>OrderEnumerator.orderEntries().withoutDepModules().getPathsList()</code> instead
   */
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, null);

  /**
   * @deprecated use <code>OrderEnumerator.orderEntries(module).withoutSdk().recursively().getPathsList()</code> or
   * <code>OrderEnumerator.orderEntries(project).withoutSdk().getPathsList()</code> instead
   */
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASS_RECURSIVE_WO_JDK =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, null, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);

  /**
   * @deprecated use <code>OrderEnumerator.orderEntries(module).recursively().getPathsList()</code> or
   * <code>OrderEnumerator.orderEntries(project).getPathsList()</code> instead
   */
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_RECURSIVE =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);

  /**
   * @deprecated use <code>OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().recursively().getPathsList()</code>
   * or <code>OrderEnumerator.orderEntries(project).withoutSdk().productionOnly().getPathsList()</code> instead
   */
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS =
    new ProjectRootsTraversing.RootTraversePolicy(GENERAL_OUTPUT, null, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES_WITHOUT_TESTS, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE_WITHOUT_TESTS);

  /**
   * @deprecated use <code>OrderEnumerator.orderEntries(module).productionOnly().recursively().getPathsList()</code>
   * or <code>OrderEnumerator.orderEntries(project).productionOnly().getPathsList()</code> instead
   */
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_WITHOUT_TESTS =
    new ProjectRootsTraversing.RootTraversePolicy(GENERAL_OUTPUT, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES_WITHOUT_TESTS, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES_WITHOUT_TESTS, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE_WITHOUT_TESTS);

  private ProjectClasspathTraversing() {
  }

  public static class AddModuleOutput implements ProjectRootsTraversing.RootTraversePolicy.Visit<ModuleSourceOrderEntry> {
    private final boolean myIncludeTests;

    public AddModuleOutput(boolean includeTests) {
      myIncludeTests = includeTests;
    }

    public void visit(ModuleSourceOrderEntry sourceEntry, ProjectRootsTraversing.TraverseState traverseState, RootPolicy<ProjectRootsTraversing.TraverseState> policy) {
      traverseState.addAllUrls(getOutputs(traverseState.getCurrentModuleManager().getModule()));
    }

    public List<String> getOutputs(Module module) {
      List<String> outputs = new SmartList<String>();
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      if (compilerModuleExtension != null) {
        String testOutput = compilerModuleExtension.getCompilerOutputUrlForTests();
        if (myIncludeTests && testOutput != null) outputs.add(testOutput);
        String output = compilerModuleExtension.getCompilerOutputUrl();
        if ((!Comparing.equal(output, testOutput) || !myIncludeTests) && output != null) {
          outputs.add(output);
        }
      }
      return outputs;
    }
  }
}
