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

class Multicatch {
  @NotNull
  public Map test(String name) {
    try (InputStream s = build()) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    } catch (SQLException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream build() throws SQLException {
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }

}