package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public class JavaFindUsagesOptions extends FindUsagesOptions {
  public boolean isClassesUsages = false;
  public boolean isMethodsUsages = false;
  public boolean isFieldsUsages = false;
  public boolean isDerivedClasses = false;
  public boolean isImplementingClasses = false;
  public boolean isDerivedInterfaces = false;
  public boolean isOverridingMethods = false;
  public boolean isImplementingMethods = false;
  public boolean isIncludeSubpackages = true;
  public boolean isSkipImportStatements = false;
  public boolean isSkipPackageStatements = false;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited = false;
  public boolean isReadAccess = false;
  public boolean isWriteAccess = false;
  public boolean isIncludeOverloadUsages = false;
  public boolean isThrowUsages = false;

  public JavaFindUsagesOptions(@NotNull Project project, @Nullable DataContext dataContext) {
    super(project, dataContext);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaFindUsagesOptions that = (JavaFindUsagesOptions)o;

    if (isCheckDeepInheritance != that.isCheckDeepInheritance) return false;
    if (isClassesUsages != that.isClassesUsages) return false;
    if (isDerivedClasses != that.isDerivedClasses) return false;
    if (isDerivedInterfaces != that.isDerivedInterfaces) return false;
    if (isFieldsUsages != that.isFieldsUsages) return false;
    if (isImplementingClasses != that.isImplementingClasses) return false;
    if (isImplementingMethods != that.isImplementingMethods) return false;
    if (isIncludeInherited != that.isIncludeInherited) return false;
    if (isIncludeOverloadUsages != that.isIncludeOverloadUsages) return false;
    if (isIncludeSubpackages != that.isIncludeSubpackages) return false;
    if (isMethodsUsages != that.isMethodsUsages) return false;
    if (isOverridingMethods != that.isOverridingMethods) return false;
    if (isReadAccess != that.isReadAccess) return false;
    if (isSearchForTextOccurrences != that.isSearchForTextOccurrences) return false;
    if (isSkipImportStatements != that.isSkipImportStatements) return false;
    if (isSkipPackageStatements != that.isSkipPackageStatements) return false;
    if (isThrowUsages != that.isThrowUsages) return false;
    if (isUsages != that.isUsages) return false;
    if (isWriteAccess != that.isWriteAccess) return false;
    if (searchScope != null ? !searchScope.equals(that.searchScope) : that.searchScope != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isClassesUsages ? 1 : 0);
    result = 31 * result + (isMethodsUsages ? 1 : 0);
    result = 31 * result + (isFieldsUsages ? 1 : 0);
    result = 31 * result + (isDerivedClasses ? 1 : 0);
    result = 31 * result + (isImplementingClasses ? 1 : 0);
    result = 31 * result + (isDerivedInterfaces ? 1 : 0);
    result = 31 * result + (isOverridingMethods ? 1 : 0);
    result = 31 * result + (isImplementingMethods ? 1 : 0);
    result = 31 * result + (isIncludeSubpackages ? 1 : 0);
    result = 31 * result + (isSkipImportStatements ? 1 : 0);
    result = 31 * result + (isSkipPackageStatements ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    result = 31 * result + (isReadAccess ? 1 : 0);
    result = 31 * result + (isWriteAccess ? 1 : 0);
    result = 31 * result + (isIncludeOverloadUsages ? 1 : 0);
    result = 31 * result + (isThrowUsages ? 1 : 0);
    return result;
  }

  @Override
  public String generateUsagesString() {
    String suffix = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    ArrayList<String> strings = new ArrayList<String>();
    if (this.isUsages || this.isClassesUsages || this.isMethodsUsages || this.isFieldsUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    if (this.isIncludeOverloadUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.overloaded.methods.usages"));
    }
    if (this.isDerivedClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.classes"));
    }
    if (this.isDerivedInterfaces) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.interfaces"));
    }
    if (this.isImplementingClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.classes"));
    }
    if (this.isImplementingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.methods"));
    }
    if (this.isOverridingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.overriding.methods"));
    }
    if (strings.isEmpty()) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    return StringUtil.join(strings, suffix);
  }


}
