/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.scripting;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryTable {

  private final static OrderRootType SOURCE_ROOT_TYPE = OrderRootType.SOURCES;
  private final static OrderRootType COMPACT_ROOT_TYPE = OrderRootType.CLASSES;

  private ArrayList<LibraryModel> myLibraryModels = new ArrayList<LibraryModel>();
  private HashSet<VirtualFile> myCompactFilesCache;
  private HashMap<String,VirtualFile> myFileNameCache;

  public ScriptingLibraryTable(@NotNull LibraryTable libraryTable, LibraryType libraryType) {
    for (Library library : libraryTable.getLibraries()) {
      if (library instanceof LibraryEx) {
        LibraryType libType = ((LibraryEx)library).getType();
        if (libType != null && libType.equals(libraryType)) {
          LibraryModel libModel = new LibraryModel(library.getName());
          libModel.setSourceFiles(library.getFiles(SOURCE_ROOT_TYPE));
          libModel.setCompactFiles(library.getFiles(COMPACT_ROOT_TYPE));
          libModel.setDocUrls(library.getUrls(OrderRootType.DOCUMENTATION));
          myLibraryModels.add(libModel);
        }
      }
    }
  }

  public boolean isLibraryFile(VirtualFile file) {
    for (LibraryModel libraryModel : myLibraryModels) {
      if (libraryModel.containsFile(file)) return true;
    }
    return false;
  }

  public Set<String> getDocUrlsFor(VirtualFile file) {
    Set<String> urls = new HashSet<String>();
    for (LibraryModel libraryModel : myLibraryModels) {
      if (libraryModel.containsFile(file)) {
        urls.addAll(libraryModel.getDocUrls());
      }
    }
    return urls;
  }

  public boolean isCompactFile(VirtualFile file) {
    if (myCompactFilesCache == null) {
      myCompactFilesCache = new HashSet<VirtualFile>();
      for (LibraryModel libraryModel : myLibraryModels) {
        myCompactFilesCache.addAll(libraryModel.getCompactFiles());
      }
    }
    return myCompactFilesCache.contains(file);
  }
  
  @Nullable
  public VirtualFile getMatchingFile(String fileName) {
    if (myFileNameCache == null) {
      myFileNameCache = new HashMap<String,VirtualFile>();
      for (LibraryModel libModel : myLibraryModels) {
        VirtualFile file = libModel.getMatchingFile(fileName);
        if (file != null) {
          myFileNameCache.put(fileName, file);
          return file;
        }
      }
      return null;
    }
    return myFileNameCache.get(fileName);
  }

  public void invalidateCache() {
    myCompactFilesCache = null;
    myFileNameCache = null;
  }

  @Nullable
  public LibraryModel getLibraryByName(@NotNull String libName) {
    for (LibraryModel libraryModel : myLibraryModels) {
      if (libName.equals(libraryModel.getName())) return libraryModel;
    }
    return null;
  }

  public void removeLibrary(LibraryModel libraryModel) {
    myLibraryModels.remove(libraryModel);
    invalidateCache();
  }

  public LibraryModel createLibrary(String libName) {
    LibraryModel libModel = new LibraryModel(libName);
    myLibraryModels.add(libModel);
    invalidateCache();
    return libModel;
  }

  public LibraryModel createLibrary(String libName, VirtualFile[] sourceFiles, VirtualFile[] compactFiles, String[] docUrls) {
    LibraryModel libModel = new LibraryModel(libName, sourceFiles, compactFiles, docUrls);
    myLibraryModels.add(libModel);
    invalidateCache();
    return libModel;
  }

  public LibraryModel[] getLibraries() {
    return myLibraryModels.toArray(new LibraryModel[myLibraryModels.size()]);
  }

  public int getLibCount() {
    return myLibraryModels.size();
  }

  @Nullable
  public LibraryModel getLibraryAt(int index) {
    if (index < 0 || index > myLibraryModels.size() - 1) return null;
    return myLibraryModels.get(index);
  }

  public static class LibraryModel {
    private String myName;
    private Set<VirtualFile> mySourceFiles = new HashSet<VirtualFile>();
    private Set<VirtualFile> myCompactFiles = new HashSet<VirtualFile>();
    private Set<String> myDocUrls = new TreeSet<String>(); 

    public LibraryModel(String name, VirtualFile[] sourceFiles, VirtualFile[] compactFiles, String[] docUrls) {
      this(name);
      mySourceFiles.addAll(Arrays.asList(sourceFiles));
      myCompactFiles.addAll(Arrays.asList(compactFiles));
      myDocUrls.addAll(Arrays.asList(docUrls));
    }

    public LibraryModel(String name) {
      myName = name;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setSourceFiles(VirtualFile[] files) {
      mySourceFiles.clear();
      mySourceFiles.addAll(Arrays.asList(files));
    }

    public void setCompactFiles(VirtualFile[] files) {
      myCompactFiles.clear();
      myCompactFiles.addAll(Arrays.asList(files));
    }
    
    public void setDocUrls(String[] docUrls) {
      myDocUrls.clear();
      myDocUrls.addAll(Arrays.asList(docUrls));
    }

    public Set<VirtualFile> getSourceFiles() {
      return mySourceFiles;
    }
    
    public Set<VirtualFile> getCompactFiles() {
      return myCompactFiles;
    }
    
    public Set<String> getDocUrls() {
      return myDocUrls;
    }

    @NotNull
    public Set<VirtualFile> getFiles(OrderRootType rootType) {
      if (rootType == COMPACT_ROOT_TYPE) {
        return getCompactFiles();
      }
      return getSourceFiles();
    }

    public String getName() {
      return myName;
    }

    public boolean containsFile(VirtualFile file) {
      return mySourceFiles.contains(file) || myCompactFiles.contains(file);
    }
    
    @Nullable
    public VirtualFile getMatchingFile(String fileName) {
      for (VirtualFile sourceFile : mySourceFiles) {
        if (sourceFile.getName().equals(fileName)) return sourceFile;
      }
      for (VirtualFile compactFile : myCompactFiles) {
        if (compactFile.getName().equals(fileName)) return compactFile;
      }
      return null;
    }

    public boolean isEmpty() {
      return mySourceFiles.isEmpty() && myCompactFiles.isEmpty();
    }
  }
}
