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

package com.intellij.ide.macro;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConvertingIterator;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public final class MacroManager {
  private final HashMap<String, Macro> myMacrosMap = new HashMap<>();

  public static MacroManager getInstance() {
    return ServiceManager.getService(MacroManager.class);
  }

  private MacroManager() {
    registerMacro(new SourcepathMacro());
    registerMacro(new FileDirMacro());
    registerMacro(new FileDirNameMacro());
    registerMacro(new FileParentDirMacro());
    registerMacro(new FileDirPathFromParentMacro());
    registerMacro(new FileExtMacro());
    registerMacro(new FileNameMacro());
    registerMacro(new FileNameWithoutExtension());
    registerMacro(new FileNameWithoutAllExtensions());
    registerMacro(new FilePathMacro());
    registerMacro(new UnixSeparatorsMacro());
    registerMacro(new FileEncodingMacro());
    registerMacro(new FileDirRelativeToProjectRootMacro());
    registerMacro(new FilePathRelativeToProjectRootMacro());
    registerMacro(new FileDirRelativeToSourcepathMacro());
    registerMacro(new FilePathRelativeToSourcepathMacro());
    registerMacro(new JdkPathMacro());
    registerMacro(new PromptMacro());
    registerMacro(new FilePromptMacro());
    registerMacro(new SourcepathEntryMacro());
    registerMacro(new ProjectFileDirMacro());
    registerMacro(new ProjectNameMacro());
    registerMacro(new ProjectPathMacro());

    registerMacro(new ModuleFilePathMacro());
    registerMacro(new ModuleFileDirMacro());
    registerMacro(new ModuleNameMacro());
    registerMacro(new ModulePathMacro());
    registerMacro(new ModuleSdkPathMacro());

    registerMacro(new FileRelativePathMacro());
    registerMacro(new FileRelativeDirMacro());
    registerMacro(new LineNumberMacro());
    registerMacro(new ColumnNumberMacro());

    registerMacro(new ClipboardContentMacro());
    registerMacro(new SelectedTextMacro());
    registerMacro(new SelectionStartLineMacro());
    registerMacro(new SelectionStartColumnMacro());
    registerMacro(new SelectionEndLineMacro());
    registerMacro(new SelectionEndColumnMacro());

    if (File.separatorChar != '/') {
      registerMacro(new FileDirRelativeToProjectRootMacro2());
      registerMacro(new FilePathRelativeToProjectRootMacro2());
      registerMacro(new FileDirRelativeToSourcepathMacro2());
      registerMacro(new FilePathRelativeToSourcepathMacro2());
      registerMacro(new FileRelativeDirMacro2());
      registerMacro(new FileRelativePathMacro2());
    }
    for (Macro macro : Extensions.getExtensions(Macro.EP_NAME)) {
      registerMacro(macro);
    }
  }

  private void registerMacro(Macro macro) {
    assert PathMacrosImpl.getToolMacroNames().contains(macro.getName()) : "Macro '" + macro.getName() + "' should be registered in PathMacros!";

    myMacrosMap.put(macro.getName(), macro);
  }

  public Collection<Macro> getMacros() {
    return myMacrosMap.values();
  }

  public void cacheMacrosPreview(DataContext dataContext) {
    dataContext = getCorrectContext(dataContext);
    for (Macro macro : getMacros()) {
      macro.cachePreview(dataContext);
    }
  }

  private static DataContext getCorrectContext(DataContext dataContext) {
    if (PlatformDataKeys.FILE_EDITOR.getData(dataContext) != null) {
      return dataContext;
    }
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return dataContext;
    }
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    VirtualFile[] files = editorManager.getSelectedFiles();
    if (files.length == 0) {
      return dataContext;
    }
    FileEditor fileEditor = editorManager.getSelectedEditor(files[0]);
    return fileEditor == null ? dataContext : DataManager.getInstance().getDataContext(fileEditor.getComponent());
  }

  /**
   * Expands all macros that are found in the <code>str</code>.
   */
  @Nullable
  public String expandMacrosInString(String str, boolean firstQueueExpand, DataContext dataContext) throws Macro.ExecutionCancelledException {
    return expandMacroSet(str, firstQueueExpand, dataContext, getMacros().iterator());
  }

  @Nullable
  private String expandMacroSet(String str,
                                boolean firstQueueExpand, DataContext dataContext, Iterator<Macro> macros
                                ) throws Macro.ExecutionCancelledException {
    if (str == null) return null;
    while (macros.hasNext()) {
      Macro macro = macros.next();
      if (macro instanceof SecondQueueExpandMacro && firstQueueExpand) continue;
      String name = "$" + macro.getName() + "$";
      String macroNameWithParamStart = "$" + macro.getName() + "(";
      if (str.contains(name)) {
        String expanded = macro.expand(dataContext);
        //if (dataContext instanceof DataManagerImpl.MyDataContext) {
        //  // hack: macro.expand() can cause UI events such as showing dialogs ('Prompt' macro) which may 'invalidate' the datacontext
        //  // since we know exactly that context is valid, we need to update its event count
        //  ((DataManagerImpl.MyDataContext)dataContext).setEventCount(IdeEventQueue.getInstance().getEventCount());
        //}
        if (expanded == null) {
          expanded = "";
        }
        str = StringUtil.replace(str, name, expanded);
      }
      else if(str.contains(macroNameWithParamStart)) {
        String macroNameWithParamEnd = ")$";
        Map<String, String> toReplace = null;
        int i = str.indexOf(macroNameWithParamStart);
        while (i != -1) {
          int j = str.indexOf(macroNameWithParamEnd, i + macroNameWithParamStart.length());
          if(j > i) {
            String param = str.substring(i + macroNameWithParamStart.length(), j);
            if(toReplace == null) toReplace = new THashMap<>();
            String expanded = macro.expand(dataContext, param);
            if (expanded == null) {
              expanded = "";
            }
            toReplace.put(macroNameWithParamStart + param + macroNameWithParamEnd, expanded);
            i = j + macroNameWithParamEnd.length();
          } else {
            break;
          }
        }
        if(toReplace !=null) {
          for (Map.Entry<String, String> entry : toReplace.entrySet()) {
            str = StringUtil.replace(str, entry.getKey(), entry.getValue());
          }
        }
      }
    }
    return str;
  }

  public String expandSilentMarcos(String str, boolean firstQueueExpand, DataContext dataContext) throws Macro.ExecutionCancelledException {
    final Convertor<Macro, Macro> convertor = new Convertor<Macro, Macro>() {
      @Override
      public Macro convert(Macro macro) {
        if (macro instanceof PromptingMacro) {
          return new Macro.Silent(macro, "");
        }
        return macro;
      }
    };
    return expandMacroSet(
      str, firstQueueExpand, dataContext, ConvertingIterator.create(getMacros().iterator(), convertor)
    );
  }

}
