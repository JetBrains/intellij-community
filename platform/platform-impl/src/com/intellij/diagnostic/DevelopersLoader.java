/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

class DevelopersLoader {
  private static final String DEVELOPERS_LIST_URL = "http://ea-engine.labs.intellij.net/data?category=developers";

  private DevelopersLoader() { }

  public static Collection<Developer> fetchDevelopers(@NotNull ProgressIndicator indicator) throws IOException {
    return HttpRequests.request(DEVELOPERS_LIST_URL).connect(new HttpRequests.RequestProcessor<Collection<Developer>>() {
      @Override
      public Collection<Developer> process(@NotNull HttpRequests.Request request) throws IOException {
        List<Developer> developers = new LinkedList<>();
        developers.add(Developer.NULL);

        String line;
        while ((line = request.getReader().readLine()) != null) {
          int i = line.indexOf('\t');
          if (i == -1) throw new IOException("Protocol error");
          int id = Integer.parseInt(line.substring(0, i));
          String name = line.substring(i + 1);
          developers.add(new Developer(id, name));
          indicator.checkCanceled();
        }

        return developers;
      }
    });
  }
}