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
package com.intellij.compiler;

import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/18/12
 */
public abstract class ProblemsView {

  public static class SERVICE {
    private SERVICE() {
    }

    public static ProblemsView getInstance(Project project) {
      return ServiceManager.getService(project, ProblemsView.class);
    }
  }

  public abstract void clearMessages(CompileScope scope);
  
  public abstract void clearMessages();

  public abstract void addMessage(int type, @NotNull String[] text, @Nullable VirtualFile file, int line, int column, @Nullable Object data);

  public abstract void addMessage(int type, @NotNull String[] text, @Nullable VirtualFile underFileGroup, @Nullable VirtualFile file, int line, int column, @Nullable Object data);

  public abstract void addMessage(int type, @NotNull String[] text, @Nullable String groupName, @NotNull Navigatable navigatable, @Nullable String exportTextPrefix, @Nullable String rendererTextPrefix, @Nullable Object data);

  public void addMessage(CompilerMessage message) {
    final Navigatable navigatable = message.getNavigatable();
    final VirtualFile file = message.getVirtualFile();
    final CompilerMessageCategory category = message.getCategory();
    final int type = CompilerTask.translateCategory(category);
    final String[] text = convertMessage(message);
    if (navigatable != null) {
      final String groupName = file != null? file.getPresentableUrl() : category.getPresentableText();
      addMessage(type, text, groupName, navigatable, message.getExportTextPrefix(), message.getRenderTextPrefix(), message.getVirtualFile());
    }
    else {
      addMessage(type, text, file, -1, -1, message.getVirtualFile());
    }
  }

  public abstract void setProgress(String text, float fraction);
  
  public abstract void setProgress(String text);

  public abstract void clearProgress();

  private static String[] convertMessage(final CompilerMessage message) {
    String text = message.getMessage();
    if (!text.contains("\n")) {
      return new String[]{text};
    }
    final List<String> lines = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(text, "\n", false);
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    return ArrayUtil.toStringArray(lines);
  }
  
}
