package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;

import java.util.List;
import java.util.ArrayList;

public class ProjectClasspathTraversing {
  public static final AddModuleOutput ALL_OUTPUTS = new AddModuleOutput(true);
  public static final AddModuleOutput GENERAL_OUTPUT = new AddModuleOutput(false);

  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, null);
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASS_RECURSIVE_WO_JDK =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, null, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_RECURSIVE =
    new ProjectRootsTraversing.RootTraversePolicy(ALL_OUTPUTS, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);
  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS =
    new ProjectRootsTraversing.RootTraversePolicy(GENERAL_OUTPUT, null, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);

  public static final ProjectRootsTraversing.RootTraversePolicy FULL_CLASSPATH_WITHOUT_TESTS =
    new ProjectRootsTraversing.RootTraversePolicy(GENERAL_OUTPUT, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES, ProjectRootsTraversing.RootTraversePolicy.RECURSIVE);

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
      ArrayList<String> outputs = new ArrayList<String>();
      final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
      String testOutput = compilerModuleExtension.getCompilerOutputUrlForTests();
      if (myIncludeTests && testOutput != null) outputs.add(testOutput);
      String output = compilerModuleExtension.getCompilerOutputUrl();
      if ((!Comparing.equal(output, testOutput) || !myIncludeTests) && output != null) {
        outputs.add(output);
      }
      return outputs;
    }
  }
}
