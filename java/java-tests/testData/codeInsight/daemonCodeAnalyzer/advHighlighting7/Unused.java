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
import java.io.*;
import java.net.*;

class UnusedDeclBug {
    public static enum Concern {
        // These fields are used!  Just because I don't mention them by name
        // doesn't mean they aren't used!
        // IDEA tells me I need: @SuppressWarnings({"UnusedDeclaration"})
        LOW,
        MEDIUM,
        HIGH;
    };

    public static void main(String[] args) {
        System.out.println("Concerns are:");

        // Invoking Concern.values() should count as using all the fields in the
        // enum.
        for (Concern concern : Concern.values()) {
            System.out.print("\t");
            System.out.println(concern);
        } // end for
    }
}

class ForEachTest {
  public static void main(String[] args) {
    int count = 0;
    for (String ignore : args) {
      count++;
    }
    System.out.println(count);
  }
}

class TryWithResourcesTest {
  public static void main(String[] args) {
    System.out.println(checkUrl("bad url"));
  }

  private static String checkUrl(String url) {
    try {
      URLConnection connection = new URL(url).openConnection();
      try (InputStream ignored = connection.getInputStream()) {
        return connection.getURL().toString();
      }
    }
    catch (IOException e) {
      return null;
    }
  }
}