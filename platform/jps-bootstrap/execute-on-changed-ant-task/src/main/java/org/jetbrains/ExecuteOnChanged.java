package org.jetbrains;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.FileResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class ExecuteOnChanged extends Task implements TaskContainer {
  private Task nestedTask;
  private Path myInputs;
  private Path myOutputs;
  private File myStateFile;

  @Override
  public synchronized void addTask(Task t) {
    if (nestedTask != null) {
      throw new BuildException(
        "The executeOnChanged task container accepts a single nested task (which may be a <sequential> task container)");
    }
    nestedTask = t;
  }

  public void setStateFile(File file) {
    myStateFile = file;
  }

  @SuppressWarnings("unused")
  public void setInputs(Path s) {
    createInputs().append(s);
  }

  @SuppressWarnings("unused")
  public void setInputsRef(Reference r) {
    createInputs().setRefid(r);
  }

  public Path createInputs() {
    if (myInputs == null) {
      myInputs = new Path(getProject());
    }
    return myInputs;
  }

  @SuppressWarnings("unused")
  public void setOutputs(Path s) {
    createOutputs().append(s);
  }

  @SuppressWarnings("unused")
  public void setOutputsRef(Reference r) {
    createOutputs().setRefid(r);
  }

  public Path createOutputs() {
    if (myOutputs == null) {
      myOutputs = new Path(getProject());
    }
    return myOutputs;
  }

  private static void recordFile(StringBuilder result, java.nio.file.Path path) {
    try {
      result.append(path)
        .append(" ").append(Files.size(path))
        .append(" ").append(Files.getLastModifiedTime(path))
        .append("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String buildManifest(Path inputs, boolean required) throws IOException {
    StringBuilder result = new StringBuilder();

    List<Resource> sortedResources = inputs.stream()
      .sorted(Comparator.comparing(Resource::toString))
      .collect(Collectors.toList());

    for (Resource resource : sortedResources) {
      if (!(resource instanceof FileResource)) {
        throw new BuildException("Only FileResource resources are supported: " + resource.toLongString());
      }

      java.nio.file.Path path = ((FileResource) resource).getFile().toPath();
      if (!Files.exists(path)) {
        if (required) {
          throw new BuildException("Required file or directory is missing on disk: " + path);
        }
        else {
          result.append(resource).append(": missing\n");
        }
      }
      else {
        if (Files.isDirectory(path)) {
          try (Stream<java.nio.file.Path> pathStream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
            pathStream
              .sorted(java.nio.file.Path::compareTo)
              .filter(p -> !Files.isDirectory(p))
              .forEach(p -> recordFile(result, p));
          }
        }
        else {
          recordFile(result, path);
        }
      }
    }

    return result.toString();
  }

  private boolean isDirty(boolean requireOutputs) throws IOException {
    if (!myStateFile.exists()) {
      log("State file '" + myStateFile + "' is missing, building...");
      return true;
    }

    java.nio.file.Path stateFile = myStateFile.toPath();
    String actualContent = Files.readString(stateFile);
    String expectedContent = buildManifest(myInputs, true) + "\n" + buildManifest(myOutputs, requireOutputs);

    if (!expectedContent.equals(actualContent)) {
      log("Inputs are changed according to state file " + myStateFile + ", rebuilding...");
      log("expected:\n" + expectedContent + "\n\nactual:\n" + actualContent + "\n", Project.MSG_VERBOSE);
      return true;
    }

    return false;
  }

  @Override
  public void execute() throws BuildException {
    if (myInputs == null || myInputs.isEmpty()) {
      throw new BuildException("Some inputs must be specified");
    }

    if (myOutputs == null || myOutputs.isEmpty()) {
      throw new BuildException("Some outputs must be specified");
    }

    if (myStateFile == null) {
      throw new BuildException("stateFile is not set");
    }

    try {
      if (isDirty(false)) {
        nestedTask.perform();

        java.nio.file.Path stateFilePath = myStateFile.toPath();
        Files.createDirectories(stateFilePath.getParent());
        Files.writeString(stateFilePath, buildManifest(myInputs, true) + "\n" + buildManifest(myOutputs, true));

        if (isDirty(true)) {
          throw new BuildException("Not UP-TO-DATE after writing state file at " + myStateFile);
        }
      }
      else {
        log("UP-TO-DATE according to state file " + myStateFile);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
