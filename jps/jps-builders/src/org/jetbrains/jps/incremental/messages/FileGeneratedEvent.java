/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class FileGeneratedEvent extends BuildMessage {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.messages.FileGeneratedEvent");

  private final Collection<Pair<String, String>> myPaths = new ArrayList<Pair<String, String>>();

  public FileGeneratedEvent() {
    super("", Kind.INFO);
  }

  public void add(String root, String relativePath) {
    if (root != null && relativePath != null) {
      myPaths.add(Pair.create(FileUtil.toSystemIndependentName(root), FileUtil.toSystemIndependentName(relativePath)));
    }
    else {
      LOG.info("Invalid file generation event: root=" + root + "; relativePath=" + relativePath);
    }
  }

  public Collection<Pair<String, String>> getPaths() {
    return myPaths;
  }
}
