package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaVariableFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isReadAccess = true;
  public boolean isWriteAccess = true;

  public JavaVariableFindUsagesOptions(@NotNull Project project) {
    super(project);
    isSearchForTextOccurrences = false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaVariableFindUsagesOptions that = (JavaVariableFindUsagesOptions)o;

    if (isReadAccess != that.isReadAccess) return false;
    if (isWriteAccess != that.isWriteAccess) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isReadAccess ? 1 : 0);
    result = 31 * result + (isWriteAccess ? 1 : 0);
    return result;
  }

}
