package jetbrains.antlayout.datatypes;

import java.io.File;

/**
 * @author max
 */
public class IdeaModule extends IdeaModuleBase {
  public IdeaModule() {
  }

  public IdeaModule(IdeaModule fileset) {
    super(fileset);
  }

  protected String getKind() {
    return "production";
  }

  protected String getOutputDirProperty() {
    return "module." + getName() + ".output.main";
  }
}
