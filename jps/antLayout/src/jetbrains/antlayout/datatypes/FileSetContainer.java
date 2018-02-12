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
import org.apache.tools.ant.types.FileSet;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class FileSetContainer extends Content {
  private final FileSet fileSet;

  public FileSetContainer(FileSet fileSet) {
    this.fileSet = fileSet;
  }

  @Override
  public List<LayoutFileSet> build(TempFileFactory temp) {
    return Collections.singletonList(Container.createCopy(fileSet));
  }

  @Override
  public void validateArguments() throws BuildException {
    if (fileSet instanceof IdeaModuleBase) {
      ((IdeaModuleBase) fileSet).validateArguments();
    }
  }
}
