/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit.segments;

import java.io.*;
import java.util.Vector;

class TraceFilter {
  private final String myTrace;
  private final Vector myLines = new Vector();

  public TraceFilter(String trace) {
    myTrace = trace;
  }

  public String execute() {
    try {
      readLines();
    } catch (IOException e) {
      return myTrace;
    }
    int traceLastLine = firstJUnitLine(myLines.size() - 1, true);
    if (traceLastLine < 0) return "";
    int traceFirstLine = firstJUnitLine(traceLastLine, false);
    StringWriter buffer = new StringWriter();
    PrintWriter writer = new PrintWriter(buffer);
    for (int i = 0; i < traceFirstLine; i++) writer.println(myLines.elementAt(i));
    for (int i = traceLastLine; i < myLines.size(); i++) writer.println(myLines.elementAt(i));
    writer.flush();
    return buffer.toString();
  }

  private int firstJUnitLine(int startFrom, boolean searchForJUnitLines) {
    for (int i = startFrom; i >= 0; i--) {
      String line = (String) myLines.elementAt(i);
      if (isIdeaJUnit(line) == searchForJUnitLines) return i;
    }
    return startFrom;
  }

  /**
   * @noinspection HardCodedStringLiteral
   */
  private boolean isIdeaJUnit(String line) {
    return line.indexOf("com.intellij.rt") >= 0;
  }

  private void readLines() throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(myTrace));
    String line;
    while ((line = reader.readLine()) != null) { myLines.addElement(line); }
    reader.close();
  }
}
