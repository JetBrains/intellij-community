package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.types.ZipFileSet;

import java.io.File;

/**
 * @author nik
 */
public class IdeaModuleTests extends IdeaModuleBase {
  public IdeaModuleTests() {
  }

  public IdeaModuleTests(ZipFileSet fileset) {
    super(fileset);
  }

  @Override
  protected String getOutputDirProperty() {
    return "module." + getName() + ".output.test";
  }

  @Override
  protected String getKind() {
    return "test";
  }
}
