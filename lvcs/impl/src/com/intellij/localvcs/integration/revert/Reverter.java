package com.intellij.localvcs.integration.revert;

import java.io.IOException;
import java.util.List;

public abstract class Reverter {
  public abstract List<String> checkCanRevert() throws IOException;

  public abstract void revert() throws IOException;
}
