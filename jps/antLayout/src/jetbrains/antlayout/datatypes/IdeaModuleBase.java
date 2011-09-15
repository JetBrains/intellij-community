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
    String common = getProject().getProperty("modules.output");
    if (common != null) {
      return new File(new File(common), getKind() + "/" + getName());
    }

    String adhoc = getProject().getProperty(getOutputDirProperty());
    return adhoc != null ? new File(adhoc) : null;
  }

  public void validateArguments() throws BuildException {
    if (name == null) {
      throw new BuildException("name attribute must be specified for module entry");
    }

    File outputDir = getOutputDir();

    if (outputDir == null || !outputDir.exists()) {
      throw new BuildException("No " + getKind() + " output found for module " + name +
          ". Either modules.output property references project output that doesn't contain this module or " +
          getOutputDirProperty() + " is not defined or references non-existing directory.");
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
