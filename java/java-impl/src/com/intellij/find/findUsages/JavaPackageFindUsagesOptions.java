package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public class JavaPackageFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isClassesUsages = false;
  public boolean isIncludeSubpackages = true;
  public boolean isSkipPackageStatements = false;

  public JavaPackageFindUsagesOptions(@NotNull Project project) {
    super(project);
  }

  protected void addUsageTypes(LinkedHashSet<String> to) {
    if (this.isUsages || this.isClassesUsages) {
      to.add(FindBundle.message("find.usages.panel.title.usages"));
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaPackageFindUsagesOptions that = (JavaPackageFindUsagesOptions)o;

    if (isClassesUsages != that.isClassesUsages) return false;
    if (isIncludeSubpackages != that.isIncludeSubpackages) return false;
    if (isSkipPackageStatements != that.isSkipPackageStatements) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isClassesUsages ? 1 : 0);
    result = 31 * result + (isIncludeSubpackages ? 1 : 0);
    result = 31 * result + (isSkipPackageStatements ? 1 : 0);
    return result;
  }

}
