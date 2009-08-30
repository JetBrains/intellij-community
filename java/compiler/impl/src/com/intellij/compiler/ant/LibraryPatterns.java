package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;

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
        final boolean relative = project instanceof ProjectEx && ((ProjectEx)project).isSavePathsRelative();
        final String path = GenerationUtils
          .toRelativePath(m.getPresentableString(), BuildProperties.getProjectBaseDir(project), BuildProperties.getProjectBaseDirProperty(),
                          genOptions, !relative);
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
