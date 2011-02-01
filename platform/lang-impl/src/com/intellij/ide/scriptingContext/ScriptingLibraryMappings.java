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
package com.intellij.ide.scriptingContext;

import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryMappings extends LanguagePerFileMappings<ScriptingLibraryTable.LibraryModel> implements LibraryTable.Listener,
                                                                                                                     Disposable {

  private final ScriptingLibraryManager myLibraryManager;
  private final Map<VirtualFile, CompoundLibrary> myCompoundLibMap = new HashMap<VirtualFile, CompoundLibrary>();
  private CompoundLibrary myProjectLibs = new CompoundLibrary();

  public ScriptingLibraryMappings(final Project project, final LibraryType libraryType) {
    super(project);
    myLibraryManager = new ScriptingLibraryManager(project, libraryType);
    if (myLibraryManager.ensureModel()) {
      LibraryTable libTable = myLibraryManager.getLibraryTable();
      if (libTable != null) libTable.addListener(this, this);
    }
    Disposer.register(project, this);
  }

  protected String serialize(final ScriptingLibraryTable.LibraryModel library) {
    if (library instanceof CompoundLibrary) {
      return "{" + library.getName() + "}";
    }
    return library.getName();
  }

  public void reset() {
    myLibraryManager.reset();
    for (CompoundLibrary container: myCompoundLibMap.values()) {
      container.reset();
    }
    myProjectLibs.reset();
  }
  
  private void updateMappings() {
    myLibraryManager.reset();
    Map<VirtualFile,ScriptingLibraryTable.LibraryModel> map = getMappings();
    for (VirtualFile file : map.keySet()) {
      ScriptingLibraryTable.LibraryModel value = getImmediateMapping(file);
      if (value instanceof CompoundLibrary) {
        CompoundLibrary container = (CompoundLibrary) value;
        ScriptingLibraryTable.LibraryModel[] libModels =
          container.getLibraries().toArray(new ScriptingLibraryTable.LibraryModel[container.getLibraryCount()]);
        CompoundLibrary newContainer = new CompoundLibrary();
        for (ScriptingLibraryTable.LibraryModel libraryModel : libModels) {
          String libName = libraryModel.getName();
          if (myLibraryManager.getLibraryByName(libName) != null) {
            newContainer.addLibrary(libraryModel);
          }
        }
        newContainer.applyChanges();
        setMapping(file, newContainer.isEmpty() ? null : newContainer);
      }
    }
    
  }

  @Override
  public void setMappings(Map<VirtualFile, ScriptingLibraryTable.LibraryModel> mappings) {
    super.setMappings(mappings);
    updateDependencies(mappings);
  }

  private static boolean dependencyExists(ModuleRootManager rootManager, Library library) {
    final OrderEntry[] orderEntries = rootManager.getOrderEntries();
    for (final OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        final Library moduleLibrary = libraryOrderEntry.getLibrary();
        if (moduleLibrary != null && moduleLibrary.equals(library)) return true;
      }
    }
    return false;
  }

  private void updateDependencies(final Map<VirtualFile, ScriptingLibraryTable.LibraryModel> mappings) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final LibraryTable libTable = myLibraryManager.getLibraryTable();
        if (libTable == null) return;
        final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
        for (Module module : modules) {
          final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
          //
          // Collect libraries to add to the module
          //
          Set<Library> librariesToAdd = new HashSet<Library>();
          for (VirtualFile file : mappings.keySet()) {
            //
            // In case if there is a single module (like in Web/PhpStorm), just use it without checking whether it contains the source file 
            // or not. Otherwise add dependency to a module containing the source file.
            // Note: file == null means project.
            //
            if (file == null || module.getModuleScope().contains(file) || modules.length == 1) {
              ScriptingLibraryTable.LibraryModel container = mappings.get(file);
              assert container instanceof CompoundLibrary;
              for (ScriptingLibraryTable.LibraryModel libModel : ((CompoundLibrary)container).getLibraries()) {
                final Library library = myLibraryManager.getOriginalLibrary(libModel);
                if (!dependencyExists(rootManager, library)) {
                  librariesToAdd.add(library);
                }
              }
            }
          }
          //
          // Add collected libraries (if any)
          //
          if (!librariesToAdd.isEmpty()) {
            ModifiableRootModel model = rootManager.getModifiableModel();
            for (Library library : librariesToAdd) {
              model.addLibraryEntry(library);
            }
            model.commit();
          }
        }
      }
    });
  }

  /**
   * Creates an association between a virtual file and a library specified by name.
   * @param file    The file to associate the library with.
   * @param libName The library name.
   */
  public void associate(VirtualFile file, String libName) {
    ScriptingLibraryTable.LibraryModel libraryModel = myLibraryManager.getLibraryByName(libName);
    if (libraryModel == null) return;
    ScriptingLibraryTable.LibraryModel container = getImmediateMapping(file);
    if (container == null || !(container instanceof CompoundLibrary)) {
      container = new CompoundLibrary();
    }
    if (!((CompoundLibrary)container).containsLibrary(libName)) {
      ((CompoundLibrary)container).toggleLibrary(libraryModel);
      setMapping(file, container);
    }
    updateDependencies(getMappings());
  }
  
  public boolean isAssociatedWith(VirtualFile file, String libName) {
    ScriptingLibraryTable.LibraryModel libraryModel = myLibraryManager.getLibraryByName(libName);
    if (libraryModel == null) return false;
    ScriptingLibraryTable.LibraryModel container = getImmediateMapping(file);
    if (container == null) return false;
    return ((CompoundLibrary)container).containsLibrary(libName);
  }

  @NotNull
  @Override
  protected String getValueAttribute() {
    return "libraries";
  }

  @Override
  protected ScriptingLibraryTable.LibraryModel handleUnknownMapping(VirtualFile file, String value) {
    if (value == null || !value.contains("{")) return null;
    String[] libNames = value.replace('{',' ').replace('}', ' ').split(",");
    CompoundLibrary compoundLib = new CompoundLibrary();
    for (String libName : libNames) {
      ScriptingLibraryTable.LibraryModel libraryModel = myLibraryManager.getLibraryByName(libName.trim());
      if (libraryModel != null) {
        compoundLib.toggleLibrary(libraryModel);
      }
    }
    compoundLib.applyChanges();
    if (file == null) {
      myProjectLibs = compoundLib;
    }
    else {
      myCompoundLibMap.put(file, compoundLib);
    }
    return compoundLib;
  }

  @Override
  public Collection<ScriptingLibraryTable.LibraryModel> getAvailableValues(VirtualFile file) {
    List<ScriptingLibraryTable.LibraryModel> libraries = getSingleLibraries();
    if (myCompoundLibMap.containsKey(file)) {
      libraries.add(myCompoundLibMap.get(file));
      return libraries;
    }
    CompoundLibrary compoundLib = new CompoundLibrary();
    myCompoundLibMap.put(file, compoundLib);
    libraries.add(compoundLib);
    return libraries;
  }

  @Override
  @Nullable
  public ScriptingLibraryTable.LibraryModel chosenToStored(VirtualFile file, ScriptingLibraryTable.LibraryModel value) {
    if (value instanceof CompoundLibrary) return value;
    CompoundLibrary compoundLib = file == null ? myProjectLibs : myCompoundLibMap.get(file);
    if (value == null) {
      if (compoundLib != null) {
        compoundLib.clearLibraries();
        myCompoundLibMap.remove(file);
        compoundLib = null;
      }
    }
    else {
      if (compoundLib == null) {
        compoundLib = new CompoundLibrary();
        myCompoundLibMap.put(file, compoundLib);
      }
      compoundLib.toggleLibrary(value);
      if (compoundLib.isEmpty()) {
        myCompoundLibMap.remove(file);
        compoundLib = null;
      }
    }
    return compoundLib;
  }

  @Override
  public boolean isSelectable(ScriptingLibraryTable.LibraryModel value) {
    return !(value instanceof CompoundLibrary);
  }

  @Override
  protected List<ScriptingLibraryTable.LibraryModel> getAvailableValues() {
    return getSingleLibraries();
  }


  @Override
  protected ScriptingLibraryTable.LibraryModel getDefaultMapping(@Nullable VirtualFile file) {
    return null;
  }

  public List<ScriptingLibraryTable.LibraryModel> getSingleLibraries() {
    ArrayList<ScriptingLibraryTable.LibraryModel> libraryModels = new ArrayList<ScriptingLibraryTable.LibraryModel>();
    libraryModels.addAll(Arrays.asList(myLibraryManager.getLibraries()));
    return libraryModels;
  }

  @Override
  public void afterLibraryAdded(Library newLibrary) {
    updateMappings();
  }

  @Override
  public void afterLibraryRenamed(Library library) {
    updateMappings();
  }

  @Override
  public void beforeLibraryRemoved(Library library) {
  }

  @Override
  public void afterLibraryRemoved(Library library) {
    updateMappings();
  }

  @Override
  public void dispose() {
  }

  public static class CompoundLibrary extends  ScriptingLibraryTable.LibraryModel {
    private final Map<String, ScriptingLibraryTable.LibraryModel> myLibraries = new TreeMap<String, ScriptingLibraryTable.LibraryModel>();
    private final Map<String, ScriptingLibraryTable.LibraryModel> myOldLibraries = new TreeMap<String, ScriptingLibraryTable.LibraryModel>(); 

    public CompoundLibrary() {
      super(null);
    }

    public void clearLibraries() {
      myLibraries.clear();
    }

    public void toggleLibrary(@NotNull ScriptingLibraryTable.LibraryModel library) {
      String libName = library.getName();
      if (myLibraries.containsKey(libName)) {
        myLibraries.remove(libName);
        return;
      }
      myLibraries.put(libName, library);
    }
    
    private void addLibrary(@NotNull ScriptingLibraryTable.LibraryModel library) {
      final String libName = library.getName();
      myLibraries.put(libName, library);
    }

    public boolean containsLibrary(String libName) {
      return myLibraries.containsKey(libName);
    }

    @Override
    public String getName() {
      StringBuffer allNames = new StringBuffer();
      boolean isFirst = true;
      for (ScriptingLibraryTable.LibraryModel library : myLibraries.values()) {
        allNames.append(isFirst ? "" : ", ");
        allNames.append(library.getName());
        isFirst = false;
      }
      return allNames.toString();
    }

    @Override
    public boolean containsFile(VirtualFile file) {
      for (ScriptingLibraryTable.LibraryModel library : myLibraries.values()) {
        if (library.containsFile(file)) return true;
      }
      return false;
    }
    
    public Collection<ScriptingLibraryTable.LibraryModel> getLibraries() {
      return myLibraries.values();
    }

    @Override
    public boolean isEmpty() {
      return myLibraries.isEmpty();
    }
    
    public int getLibraryCount() {
      return myLibraries.size();
    }
    
    public void applyChanges() {
      myOldLibraries.clear();
      myOldLibraries.putAll(myLibraries);
    }


    public boolean isModified() {
      if (myOldLibraries == null) return false;
      for (String libName: myLibraries.keySet()) {
        if (!myOldLibraries.containsKey(libName)) return true;
      }
      for (String libName : ArrayUtil.toStringArray(myOldLibraries.keySet())) {
        if (!myLibraries.containsKey(libName)) return true;
      }
      return false;
    }
    
    public void reset() {
      myLibraries.clear();
      myLibraries.putAll(myOldLibraries);
    }

    @Override
    public Set<VirtualFile> getSourceFiles() {
      Set<VirtualFile> sourceFiles = new HashSet<VirtualFile>();
      for (ScriptingLibraryTable.LibraryModel libModel : myLibraries.values()) {
        sourceFiles.addAll(libModel.getSourceFiles());
      }
      return sourceFiles;
    }

    @Override
    public Set<VirtualFile> getCompactFiles() {
      Set<VirtualFile> compactFiles = new HashSet<VirtualFile>();
      for (ScriptingLibraryTable.LibraryModel libModel : myLibraries.values()) {
        compactFiles.addAll(libModel.getCompactFiles());
      }
      return compactFiles;
    }
  }

  /**
   * Checks if the library file is applicable to the given source file being edited. If the file has
   * assigned libraries neither for itself nor for any of its parent directories, returns false. Parent directory
   * settings are added to source file settings: if a library file is applicable to a directory, it is also
   * applicable to any of source files under that directory.
   *
   * @param libFile The library file.
   * @param srcFile The source file to check the applicability for.
   * @return        True if applicable, false otherwise.
   */
  public boolean isApplicable(VirtualFile libFile, VirtualFile srcFile) {
    if (!myLibraryManager.isLibraryFile(libFile)) return true;
    return isRecursivelyApplicable(libFile, srcFile);
  }
  
  private boolean isRecursivelyApplicable(VirtualFile libFile, VirtualFile srcFile) {
    if (srcFile == null) return false;
    ScriptingLibraryTable.LibraryModel libraryModel = getMapping(srcFile);
    if (libraryModel != null && libraryModel.containsFile(libFile)) {
      return true;
    }
    return isApplicable(libFile, srcFile.getParent());
  }
  
  public Set<VirtualFile> getLibraryFilesFor(VirtualFile srcFile) {
    Set<VirtualFile> libFiles = new HashSet<VirtualFile>();
    collectLibraryFilesFor(srcFile, libFiles);
    return libFiles;
  }
  
  private void collectLibraryFilesFor(VirtualFile file, Set<VirtualFile> libFiles) {
    if (file == null) return;
    ScriptingLibraryTable.LibraryModel libraryModel = getMapping(file);
    if (libraryModel != null) {
      libFiles.addAll(libraryModel.getCompactFiles());
      libFiles.addAll(libraryModel.getSourceFiles());
    }
    collectLibraryFilesFor(file.getParent(), libFiles);
  }

}
