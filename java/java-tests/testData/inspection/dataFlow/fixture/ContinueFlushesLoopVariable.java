/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Map;

public class Main {
  public static String escapeAndUnescapeSymbols(String s, StringBuilder builder) {
    boolean escaped = false;
    for (int i = 0; i < s.length(); i++) {
      final char ch = s.charAt(i);
      if (escaped) {
        if (ch=='n') builder.append('\n');
        if (<warning descr="Condition 'escaped' is always 'true'">escaped</warning>) break;
        escaped = false;
        continue;
      }
      if (ch == '\\') {
        escaped = true;
        continue;
      }
      builder.append(ch);
    }
    return builder.toString();
  }
}