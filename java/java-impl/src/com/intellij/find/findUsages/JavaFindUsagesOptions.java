package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

/**
 * @author peter
 */
public abstract class JavaFindUsagesOptions extends FindUsagesOptions {
  public boolean isSkipImportStatements = false;

  public JavaFindUsagesOptions(@NotNull Project project) {
    super(project, null);

    isUsages = true;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    return isSkipImportStatements == ((JavaFindUsagesOptions)o).isSkipImportStatements;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isSkipImportStatements ? 1 : 0);
    return result;
  }

  protected void addUsageTypes(LinkedHashSet<String> to) {
    if (this.isUsages) {
      to.add(FindBundle.message("find.usages.panel.title.usages"));
    }
  }

  @Override
  public final String generateUsagesString() {
    String suffix = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    LinkedHashSet<String> strings = new LinkedHashSet<String>();
    addUsageTypes(strings);
    if (strings.isEmpty()) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    return StringUtil.join(strings, suffix);
  }


}
