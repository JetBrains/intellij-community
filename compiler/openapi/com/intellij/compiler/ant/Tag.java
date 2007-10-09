/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.compiler.ant;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Tag extends CompositeGenerator {
  public static final Tag[] EMPTY_ARRAY = new Tag[0];
  private final String myTagName;
  private final Pair[] myTagOptions;

  public Tag(@NonNls String tagName, Pair[] tagOptions) {
    myTagName = tagName;
    myTagOptions = tagOptions;
  }

  public void generate(DataOutput out) throws IOException {
    out.writeBytes("<");
    out.writeBytes(myTagName);
    if (myTagOptions != null && myTagOptions.length > 0) {
      out.writeBytes(" ");
      int generated = 0;
      for (final Pair option : myTagOptions) {
        if (option == null) {
          continue;
        }
        if (generated > 0) {
          out.writeBytes(" ");
        }
        out.writeBytes((String)option.getFirst());
        out.writeBytes("=\"");
        out.writeBytes((String)option.getSecond());
        out.writeBytes("\"");
        generated += 1;
      }
    }
    if (getGeneratorCount() > 0) {
      out.writeBytes(">");
      shiftIndent();
      try {
        super.generate(out);
      }
      finally {
        unshiftIndent();
      }
      crlf(out);
      out.writeBytes("</");
      out.writeBytes(myTagName);
      out.writeBytes(">");
    }
    else {
      out.writeBytes("/>");
    }
  }

  protected static Pair<String, String> pair(@NonNls String v1, @NonNls String v2) {
    if (v2 == null) {
      return null;
    }
    return new Pair<String, String>(v1, v2);
  }
}
