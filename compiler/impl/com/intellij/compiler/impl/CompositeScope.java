/*
 * @author: Eugene Zhuravlev
 * Date: Feb 5, 2003
 * Time: 4:17:58 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;

import java.util.*;

public class CompositeScope extends UserDataHolderBase implements CompileScope{
  private final List<CompileScope> myScopes = new ArrayList<CompileScope>();

  public CompositeScope(CompileScope scope1, CompileScope scope2) {
    addScope(scope1);
    addScope(scope2);
  }

  public CompositeScope(CompileScope[] scopes) {
    for (CompileScope scope : scopes) {
      addScope(scope);
    }
  }

  private void addScope(CompileScope scope) {
    if (scope instanceof CompositeScope) {
      final CompositeScope compositeScope = (CompositeScope)scope;
      for (CompileScope childScope : compositeScope.myScopes) {
        addScope(childScope);
      }
    }
    else {
      myScopes.add(scope);
    }
  }

  public VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly) {
    Set<VirtualFile> allFiles = new THashSet<VirtualFile>();
    for (CompileScope scope : myScopes) {
      final VirtualFile[] files = scope.getFiles(fileType, inSourceOnly);
      if (files.length > 0) {
        allFiles.addAll(Arrays.asList(files));
      }
    }
    return allFiles.toArray(new VirtualFile[allFiles.size()]);
  }

  public boolean belongs(String url) {
    for (CompileScope scope : myScopes) {
      if (scope.belongs(url)) {
        return true;
      }
    }
    return false;
  }

  public Module[] getAffectedModules() {
    Set<Module> modules = new HashSet<Module>();
    for (final CompileScope compileScope : myScopes) {
      modules.addAll(Arrays.asList(compileScope.getAffectedModules()));
    }
    return modules.toArray(new Module[modules.size()]);
  }

  public <T> T getUserData(Key<T> key) {
    for (CompileScope compileScope : myScopes) {
      T userData = compileScope.getUserData(key);
      if (userData != null) {
        return userData;
      }
    }
    return super.getUserData(key);
  }
}
