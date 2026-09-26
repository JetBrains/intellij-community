// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.antlayout.datatypes;

public class IdeaModule extends IdeaModuleBase {
  public IdeaModule() {
  }

  public IdeaModule(IdeaModule fileset) {
    super(fileset);
  }

  @Override
  protected String getKind() {
    return "production";
  }

  @Override
  protected String getOutputDirProperty() {
    return "module." + getName() + ".output.main";
  }
}
