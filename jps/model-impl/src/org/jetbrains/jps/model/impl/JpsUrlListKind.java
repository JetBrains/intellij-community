package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.JpsUrlList;

/**
 * @author nik
 */
public class JpsUrlListKind extends JpsElementKindBase<JpsUrlList> implements JpsElementCreator<JpsUrlList> {
  public JpsUrlListKind(String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public JpsUrlList create() {
    return new JpsUrlListImpl();
  }
}
