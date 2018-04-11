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
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.ant.types.PatternSet;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ExtractedDirContent extends Content {
  private String jarPath;
  private String pathInJar = "";
  private final Expand expandTask;

  public ExtractedDirContent() {
    expandTask = new Expand();
    expandTask.setTaskName("unzip");
  }

  public void setJarPath(String jarPath) {
    this.jarPath = jarPath;
  }

  public void setPathInJar(String pathInJar) {
    this.pathInJar = pathInJar;
  }

  @Override
  public List<LayoutFileSet> build(TempFileFactory temp) {
    final File outputDir = temp.allocateTempFile("extractedDir");
    expandTask.setProject(getProject());
    expandTask.setSrc(new File(jarPath.replace('/', File.separatorChar)));

    File target = outputDir;
    if (!pathInJar.endsWith("/")) {
      pathInJar += "/";
    }
    if (pathInJar.startsWith("/")) {
      pathInJar = pathInJar.substring(1);
    }
    if (pathInJar.length() > 0) {
      final PatternSet patternSet = new PatternSet();
      patternSet.createInclude().setName(pathInJar + "**");
      expandTask.addPatternset(patternSet);
      target = new File(outputDir, pathInJar.replace('/', File.separatorChar));
    }
    expandTask.setDest(outputDir);
    expandTask.perform();

    final LayoutFileSet fileSet = new LayoutFileSet();
    fileSet.setDir(target);
    return Collections.singletonList(fileSet);
  }

  @Override
  public void validateArguments() throws BuildException {
    if (jarPath == null) {
      throw new BuildException("jarPath attribute must be specified for extractedDir tag");
    }
  }
}
