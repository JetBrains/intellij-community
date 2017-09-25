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
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.ZipFileSet;

import java.io.File;

/**
 * @author nik
 */
public abstract class IdeaModuleBase extends LayoutFileSet {
  private String name;

  protected IdeaModuleBase() {
    setExcludes("classpath.index");
  }

  public IdeaModuleBase(ZipFileSet fileset) {
    super(fileset);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
    setDir(getOutputDir());
  }

  protected File getOutputDir() {
    String adhoc = getProject().getProperty(getOutputDirProperty());
    return adhoc != null ? new File(adhoc) : null;
  }

  public void validateArguments() throws BuildException {
    if (name == null) {
      throw new BuildException("name attribute must be specified for module entry");
    }

    File outputDir = getOutputDir();
    if (outputDir == null) {
      throw new BuildException("Cannot find " + getKind() + " output for module " + name + ": '" + getOutputDirProperty() + "' property isn't defined");
    }
    if (!outputDir.exists()) {
      throw new BuildException("No " + getKind() + " output found for module " + name + ": " + outputDir + " doesn't exist");
    }
  }

  protected abstract String getOutputDirProperty();

  protected abstract String getKind();

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IdeaModule that = (IdeaModule) o;
    return getDir(getProject()).equals(that.getDir(getProject()));
  }

  public int hashCode() {
    return getDir(getProject()).hashCode();
  }
}
