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
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 27-Jan-16
 */
public class MessagesContainer {
  private final Project myProject;
  private final Map<CompilerMessageCategory, Collection<CompilerMessage>> myMessages = new EnumMap<>(CompilerMessageCategory.class);

  public MessagesContainer(Project project) {
    myProject = project;
  }

  @NotNull
  public Collection<CompilerMessage> getMessages(CompilerMessageCategory category) {
    final Collection<CompilerMessage> collection = myMessages.get(category);
    if (collection == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(collection);
  }

  @Nullable
  public CompilerMessage addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum, Navigatable navigatable) {
    CompilerMessageImpl msg = new CompilerMessageImpl(myProject, category, message, findFileByUrl(url), lineNum, columnNum, navigatable);
    if (addMessage(msg)) {
      return msg;
    }
    return null;
  }

  public boolean addMessage(CompilerMessage msg) {
    Collection<CompilerMessage> messages = myMessages.get(msg.getCategory());
    if (messages == null) {
      messages = new LinkedHashSet<>();
      myMessages.put(msg.getCategory(), messages);
    }
    return messages.add(msg);
  }

  @Nullable
  private static VirtualFile findFileByUrl(@Nullable String url) {
    if (url == null) {
      return null;
    }
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null) {
      // groovy stubs may be placed in completely random directories which aren't refreshed automatically
      return VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
    }
    return file;
  }

  public int getMessageCount(CompilerMessageCategory category) {
    if (category != null) {
      Collection<CompilerMessage> collection = myMessages.get(category);
      return collection != null ? collection.size() : 0;
    }
    int count = 0;
    for (Collection<CompilerMessage> collection : myMessages.values()) {
      if (collection != null) {
        count += collection.size();
      }
    }
    return count;
  }

}
