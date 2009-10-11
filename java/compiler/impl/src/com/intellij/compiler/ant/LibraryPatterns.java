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
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Library pattern generation (based on {@link FileTypes#ARCHIVE} file type).
 */
public class LibraryPatterns extends Generator {
  /**
   * A pattern set to use
   */
  private final PatternSet myPatternSet;

  /**
   * A constructor
   *
   * @param project    a context project
   * @param genOptions a generation options
   */
  public LibraryPatterns(Project project, GenerationOptions genOptions) {
    myPatternSet = new PatternSet(BuildProperties.PROPERTY_LIBRARIES_PATTERNS);
    final FileType type = FileTypes.ARCHIVE;
    final List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(type);
    for (FileNameMatcher m : matchers) {
      if (m instanceof ExactFileNameMatcher) {
        final String path = GenerationUtils
          .toRelativePath(m.getPresentableString(), BuildProperties.getProjectBaseDir(project), BuildProperties.getProjectBaseDirProperty(),
                          genOptions);
        myPatternSet.add(new Include(path));
      }
      else {
        myPatternSet.add(new Include(m.getPresentableString()));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void generate(final PrintWriter out) throws IOException {
    myPatternSet.generate(out);
  }
}
