/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Copy;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class RenamedFileContainer extends Container {
  private String filePath;
  private String newName;
  private final Copy copyTask;

  public RenamedFileContainer() {
    copyTask = new Copy();
    copyTask.setTaskName("copy");
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public void setNewName(String newName) {
    this.newName = newName;
  }

  @Override
  public List<LayoutFileSet> build(TempFileFactory temp) {
    final LayoutFileSet fileSet = new LayoutFileSet();
    final File destFile = temp.allocateTempFile(newName);
    copyTask.setProject(getProject());
    copyTask.setFile(new File(filePath.replace('/', File.separatorChar)));
    copyTask.setTofile(destFile);
    copyTask.perform();
    fileSet.setFile(destFile);
    return Collections.singletonList(fileSet);
  }

  @Override
  public void validateArguments() throws BuildException {
    super.validateArguments();
    if (filePath == null) {
      throw new BuildException("filePath attribute must be specified for renamedFile tag");
    }
    if (newName == null) {
      throw new BuildException("newName attribute must be specified for renamedFile tag");
    }
  }
}
