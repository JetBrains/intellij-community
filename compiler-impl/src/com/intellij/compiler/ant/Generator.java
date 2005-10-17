package com.intellij.compiler.ant;

import com.intellij.util.SystemProperties;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public abstract class Generator {
  private static int ourIndent = 0;
  private static final int INDENT_SHIFT = 2;

  public abstract void generate(DataOutput out) throws IOException;

  protected static void crlf(DataOutput out) throws IOException {
    out.writeBytes(SystemProperties.getLineSeparator());
    indent(out);
  }

  protected static void shiftIndent() {
    ourIndent += INDENT_SHIFT;
  }

  protected static void unshiftIndent() {
    ourIndent -= INDENT_SHIFT;
  }

  protected static void indent(DataOutput out) throws IOException {
    for (int idx = 0; idx < ourIndent; idx++) {
      out.writeBytes(" ");
    }
  }

}
