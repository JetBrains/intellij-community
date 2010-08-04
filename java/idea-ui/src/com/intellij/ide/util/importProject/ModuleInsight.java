/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 3, 2007
 */
public class ModuleInsight {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.importProject.ModuleInsight");
  @NotNull private final ProgressIndicatorWrapper myProgress;
  
  private final Set<File> myEntryPointRoots = new HashSet<File>();
  private final List<Pair<File, String>> mySourceRoots = new ArrayList<Pair<File, String>>(); // list of Pair: [sourceRoot-> package prefix]
  private final Set<String> myIgnoredNames = new HashSet<String>();

  private final Map<File, Set<String>> mySourceRootToReferencedPackagesMap = new HashMap<File, Set<String>>();
  private final Map<File, Set<String>> mySourceRootToPackagesMap = new HashMap<File, Set<String>>();
  private final Map<File, Set<String>> myJarToPackagesMap = new HashMap<File, Set<String>>();
  private final StringInterner myInterner = new StringInterner();
  private final JavaLexer myLexer;

  private List<ModuleDescriptor> myModules;
  private List<LibraryDescriptor> myLibraries;

  public ModuleInsight(@Nullable final ProgressIndicator progress) {
    this(progress, Collections.<File>emptyList(), Collections.<Pair<File,String>>emptyList(), Collections.<String>emptySet());
  }
  
  public ModuleInsight(@Nullable final ProgressIndicator progress, List<File> entryPointRoots, List<Pair<File,String>> sourceRoots, final Set<String> ignoredNames) {
    myLexer = new JavaLexer(LanguageLevel.JDK_1_5);
    myProgress = new ProgressIndicatorWrapper(progress);
    setRoots(entryPointRoots, sourceRoots, ignoredNames);
  }

  public final void setRoots(final List<File> contentRoots, final List<Pair<File, String>> sourceRoots, final Set<String> ignoredNames) {
    myModules = null;
    myLibraries = null;
    
    myEntryPointRoots.clear();
    myEntryPointRoots.addAll(contentRoots);
    
    mySourceRoots.clear();
    mySourceRoots.addAll(sourceRoots);
    
    myIgnoredNames.clear();
    myIgnoredNames.addAll(ignoredNames);
    
    myJarToPackagesMap.clear();
    myInterner.clear();
  }

  @Nullable
  public List<LibraryDescriptor> getSuggestedLibraries() {
    return myLibraries;
  }

  @Nullable
  public List<ModuleDescriptor> getSuggestedModules() {
    return myModules;
  }

  public void scanModules() {
    myProgress.setIndeterminate(true);
    final Map<File, ModuleDescriptor> contentRootToModules = new HashMap<File, ModuleDescriptor>();

    try {
      myProgress.pushState();
      
      for (Pair<File, String> pair : mySourceRoots) {
        final File sourceRoot = pair.getFirst();
        if (myIgnoredNames.contains(sourceRoot.getName())) {
          continue;
        }
        myProgress.setText("Scanning " + sourceRoot.getPath());
        
        final HashSet<String> usedPackages = new HashSet<String>();
        mySourceRootToReferencedPackagesMap.put(sourceRoot, usedPackages);
  
        final HashSet<String> selfPackages = new HashSet<String>();
        mySourceRootToPackagesMap.put(sourceRoot, selfPackages);
        
        scanSources(sourceRoot, pair.getSecond(), usedPackages, selfPackages) ;
        usedPackages.removeAll(selfPackages); 
      }
      myProgress.popState();

      myProgress.pushState();
      myProgress.setText("Building modules layout...");
      for (File srcRoot : mySourceRootToPackagesMap.keySet()) {
        final File moduleContentRoot = myEntryPointRoots.contains(srcRoot)? srcRoot : srcRoot.getParentFile();
        ModuleDescriptor moduleDescriptor = contentRootToModules.get(moduleContentRoot);
        if (moduleDescriptor != null) { // if such module aready exists
          moduleDescriptor.addSourceRoot(moduleContentRoot, srcRoot);
        }
        else {
          moduleDescriptor = new ModuleDescriptor(moduleContentRoot, new HashSet<File>(Collections.singleton(srcRoot)));
          contentRootToModules.put(moduleContentRoot, moduleDescriptor);
        }
      }
      // build dependencies

      buildModuleDependencies(contentRootToModules);
      
      myProgress.popState();
    }
    catch (ProcessCanceledException ignored) {
    }
    
    myModules = new ArrayList<ModuleDescriptor>(contentRootToModules.values());
    final Set<String> moduleNames = new HashSet<String>();
    for (ModuleDescriptor module : myModules) {
      final String suggested = suggestUniqueName(moduleNames, module.getName());
      module.setName(suggested);
      moduleNames.add(suggested);
    }
  }

  private void buildModuleDependencies(final Map<File, ModuleDescriptor> contentRootToModules) {
    final Set<File> moduleContentRoots = contentRootToModules.keySet();

    for (File contentRoot : moduleContentRoots) {
      final ModuleDescriptor checkedModule = contentRootToModules.get(contentRoot);
      myProgress.setText2("Building library dependencies for module " + checkedModule.getName());
      
      // attach libraries
      buildJarDependencies(checkedModule);

      myProgress.setText2("Building module dependencies for module " + checkedModule.getName());
      // setup module deps
      for (File aContentRoot : moduleContentRoots) {
        final ModuleDescriptor aModule = contentRootToModules.get(aContentRoot);
        if (checkedModule.equals(aModule)) {
          continue; // avoid self-dependencies
        }
        final Set<File> aModuleRoots = aModule.getSourceRoots();
        checkModules: for (File srcRoot: checkedModule.getSourceRoots()) {
          final Set<String> referencedBySourceRoot = mySourceRootToReferencedPackagesMap.get(srcRoot);
          for (File aSourceRoot : aModuleRoots) {
            if (ContainerUtil.intersects(referencedBySourceRoot, mySourceRootToPackagesMap.get(aSourceRoot))) {
              checkedModule.addDependencyOn(aModule);
              break checkModules;
            }
          }
        }
      }
    }
  }

  private void buildJarDependencies(final ModuleDescriptor module) {
    for (File jarFile : myJarToPackagesMap.keySet()) {
      final Set<String> jarPackages = myJarToPackagesMap.get(jarFile);
      for (File srcRoot : module.getSourceRoots()) {
        if (ContainerUtil.intersects(mySourceRootToReferencedPackagesMap.get(srcRoot), jarPackages)) {
          module.addLibraryFile(jarFile);
          break;
        }
      }
    }
  }

  public void scanLibraries() {
    myProgress.setIndeterminate(true);
    myProgress.pushState();
    try {
      try {
        for (File root : myEntryPointRoots) {
          myProgress.setText("Scanning for libraries " + root.getPath());
          scanRootForLibraries(root);
        }
      }
      catch (ProcessCanceledException ignored) {
      }
      myProgress.setText("Building initial libraries layout...");
      final List<LibraryDescriptor> libraries = buildInitialLibrariesLayout(myJarToPackagesMap.keySet());
      // correct library names so that there are no duplicates 
      final Set<String> libNames = new HashSet<String>(); 
      for (LibraryDescriptor library : libraries) {
        final Collection<File> libJars = library.getJars();
        final String newName = suggestUniqueName(libNames, libJars.size() == 1? libJars.iterator().next().getName() : library.getName());
        library.setName(newName);
        libNames.add(newName);
      }
      myLibraries = libraries;
    }
    finally {
      myProgress.popState();
    }
  }
  
  private static String suggestUniqueName(Set<String> existingNames, String baseName) {
    String name = baseName;
    int index = 1;
    while (existingNames.contains(name)) {
      name = baseName + (index++);
    }
    return name;
  }

  public void merge(final ModuleDescriptor mainModule, final ModuleDescriptor module) {
    for (File contentRoot : module.getContentRoots()) {
      final File _contentRoot = appendContentRoot(mainModule, contentRoot);
      final Set<File> sources = module.getSourceRoots(contentRoot);
      for (File source : sources) {
        mainModule.addSourceRoot(_contentRoot, source);
      }
    }
    for (File jar : module.getLibraryFiles()) {
      mainModule.addLibraryFile(jar);
    }
    // fix forward dependencies
    for (ModuleDescriptor dependency : module.getDependencies()) {
      if (!mainModule.equals(dependency)) { // avoid self-dependencies
        mainModule.addDependencyOn(dependency);
      }
    }
    
    myModules.remove(module);
    // fix back dependencies
    for (ModuleDescriptor moduleDescr : myModules) {
      if (moduleDescr.getDependencies().contains(module)) {
        moduleDescr.removeDependencyOn(module);
        if (!moduleDescr.equals(mainModule)) { // avoid self-dependencies
          moduleDescr.addDependencyOn(mainModule);
        }
      }
    }
  }

  public LibraryDescriptor splitLibrary(LibraryDescriptor library, String newLibraryName, final Collection<File> jarsToExtract) {
    final LibraryDescriptor newLibrary = new LibraryDescriptor(newLibraryName, jarsToExtract);
    myLibraries.add(newLibrary);
    library.removeJars(jarsToExtract);
    if (library.getJars().size() == 0) {
      removeLibrary(library);
    }
    return newLibrary;
  }
  
  public ModuleDescriptor splitModule(final ModuleDescriptor descriptor, String newModuleName, final Collection<File> contentsToExtract) {
    ModuleDescriptor newModule = null;
    for (File root : contentsToExtract) {
      final Set<File> sources = descriptor.removeContentRoot(root);
      if (newModule == null) {
        newModule = new ModuleDescriptor(root, (sources != null) ? sources : new HashSet<File>());
      }
      else {
        if (sources != null && sources.size() > 0) {
          for (File source : sources) {
            newModule.addSourceRoot(root, source);
          }
        }
        else {
          newModule.addContentRoot(root);
        }
      }
    }
    
    if (newModule != null) {
      newModule.setName(newModuleName);
      myModules.add(newModule);
    }
    else {
      return null;
    }
    
    final Map<File, ModuleDescriptor> contentRootToModule = new HashMap<File, ModuleDescriptor>();
    for (ModuleDescriptor module : myModules) {
      final Set<File> roots = module.getContentRoots();
      for (File root : roots) {
        contentRootToModule.put(root, module);
      }
      module.clearModuleDependencies();
      module.clearLibraryFiles();
    }
    
    buildModuleDependencies(contentRootToModule);
    return newModule;
  }
  
  public void removeLibrary(LibraryDescriptor lib) {
    myLibraries.remove(lib);
  }
  
  public void moveJarsToLibrary(final LibraryDescriptor from, Collection<File> files, LibraryDescriptor to) {
    to.addJars(files);
    from.removeJars(files);
    // remove the library if it became empty
    if (from.getJars().size() == 0) {
      removeLibrary(from);
    }
  }
  
  public LibraryDescriptor extractToNewLibrary(final LibraryDescriptor from, Collection<File> jars, String libraryName) {
    final LibraryDescriptor libraryDescriptor = new LibraryDescriptor(libraryName, new HashSet<File>());
    myLibraries.add(libraryDescriptor);
    moveJarsToLibrary(from, jars, libraryDescriptor);
    return libraryDescriptor;
  }
  
  public Collection<LibraryDescriptor> getLibraryDependencies(ModuleDescriptor module) {
    final Set<LibraryDescriptor> libs = new HashSet<LibraryDescriptor>();
    for (LibraryDescriptor library : myLibraries) {
      if (ContainerUtil.intersects(library.getJars(), module.getLibraryFiles())) {
        libs.add(library);
      }
    }
    return libs;
  }
  
  private static File appendContentRoot(final ModuleDescriptor module, final File contentRoot) {
    final Set<File> moduleRoots = module.getContentRoots();
    for (File moduleRoot : moduleRoots) {
      try {
        if (FileUtil.isAncestor(moduleRoot, contentRoot, false)) {
          return moduleRoot; // no need to include a separate root
        }
        if (FileUtil.isAncestor(contentRoot, moduleRoot, true)) {
          final Set<File> currentSources = module.getSourceRoots(moduleRoot);
          module.removeContentRoot(moduleRoot);
          module.addContentRoot(contentRoot);
          for (File source : currentSources) {
            module.addSourceRoot(contentRoot, source);
          }
          return contentRoot; // no need to include a separate root
        }
      }
      catch (IOException ignored) {
      }
    }
    module.addContentRoot(contentRoot);
    return contentRoot;
  }
  
  
  private static List<LibraryDescriptor> buildInitialLibrariesLayout(final Set<File> jars) {
    final Map<File, LibraryDescriptor> rootToLibraryMap = new HashMap<File, LibraryDescriptor>();
    for (File jar : jars) {
      final File parent = jar.getParentFile();
      LibraryDescriptor lib = rootToLibraryMap.get(parent);
      if (lib == null) {
        lib = new LibraryDescriptor(parent.getName(), new HashSet<File>());
        rootToLibraryMap.put(parent, lib);
      }
      lib.addJars(Collections.singleton(jar));
    }
    return new ArrayList<LibraryDescriptor>(rootToLibraryMap.values());
  }

  private void scanSources(final File fromRoot, final String parentPackageName, final Set<String> usedPackages, final Set<String> selfPackages) {
    if (myIgnoredNames.contains(fromRoot.getName())) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      boolean includeParentName = false;
      for (File file : files) {
        if (file.isDirectory()) {
          final String subPackageName;
          final StringBuilder builder = StringBuilderSpinAllocator.alloc();
          try {
            builder.append(parentPackageName);
            if (builder.length() > 0) {
              builder.append(".");
            }
            builder.append(file.getName());
            subPackageName = builder.toString();
          }
          finally {
            StringBuilderSpinAllocator.dispose(builder);
          }
          scanSources(file, subPackageName, usedPackages, selfPackages);
        }
        else {
          if (StringUtil.endsWithIgnoreCase(file.getName(), ".java")) {
            includeParentName = true;
            scanJavaFile(file, usedPackages);
          }
        }
      }
      if (includeParentName) {
        selfPackages.add(myInterner.intern(parentPackageName));
      }
    }
  }
  
  private void scanJavaFile(File file, final Set<String> usedPackages) {
    myProgress.setText2(file.getName());
    try {
      final char[] chars = FileUtil.loadFileText(file);
      scanImportStatements(chars, myLexer, usedPackages);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }
  
  private void scanRootForLibraries(File fromRoot) {
    if (myIgnoredNames.contains(fromRoot.getName())) {
      return;
    }
    final File[] files = fromRoot.listFiles();
    if (files != null) {
      myProgress.checkCanceled();
      for (File file : files) {
        if (file.isDirectory()) {
          scanRootForLibraries(file);
        }
        else {
          final String fileName = file.getName();
          if (StringUtil.endsWithIgnoreCase(fileName, ".jar") || StringUtil.endsWithIgnoreCase(fileName, ".zip")) {
            if (!myJarToPackagesMap.containsKey(file)) {
              final HashSet<String> libraryPackages = new HashSet<String>();
              myJarToPackagesMap.put(file, libraryPackages);
              
              scanLibrary(file, libraryPackages);
            }
          }
        }
      }
    }
  }
  
  
  private void scanLibrary(File file, Set<String> libraryPackages) {
    myProgress.pushState();
    myProgress.setText2(file.getName());
    try {
      final ZipFile zip = new ZipFile(file);
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while(entries.hasMoreElements()) {
        final String entryName = entries.nextElement().getName();
        if (StringUtil.endsWithIgnoreCase(entryName, ".class")) {
          final int index = entryName.lastIndexOf('/');
          if (index > 0) {
            final String packageName = entryName.substring(0, index).replace('/', '.');
            if (!libraryPackages.contains(packageName)) {
              libraryPackages.add(myInterner.intern(packageName));
            }
          }
        }
      }
      zip.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (InternalError e) { // indicates that zip file is somehow damaged and cannot be processed
      LOG.info(e);
    }
    finally {
      myProgress.popState();
    }
  }
  
  private void scanImportStatements(char[] text, final Lexer lexer, final Set<String> usedPackages){
    lexer.start(new CharArrayCharSequence(text));
    
    skipWhiteSpaceAndComments(lexer);
    if (lexer.getTokenType() == JavaTokenType.PACKAGE_KEYWORD) {
      advanceLexer(lexer);
      if (readPackageName(text, lexer) == null) {
        return;
      }
    }
    
    while (true) {
      if (lexer.getTokenType() == JavaTokenType.SEMICOLON) {
        advanceLexer(lexer);
      }
      if (lexer.getTokenType() != JavaTokenType.IMPORT_KEYWORD) {
        return;
      }
      advanceLexer(lexer);
      
      boolean isStaticImport = false;
      if (lexer.getTokenType() == JavaTokenType.STATIC_KEYWORD) {
        isStaticImport = true;
        advanceLexer(lexer);
      }
      
      final String packageName = readPackageName(text, lexer);
      if (packageName == null) {
        return;
      }
      
      if (packageName.endsWith(".*")) {
        usedPackages.add(myInterner.intern(packageName.substring(0, packageName.length() - ".*".length())));
      }
      else {
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
          String _packageName = packageName.substring(0, lastDot);
          if (isStaticImport) {
            lastDot = _packageName.lastIndexOf('.');
            _packageName = lastDot > 0? _packageName.substring(0, lastDot) : null;
          }
          if (_packageName != null) {
            usedPackages.add(myInterner.intern(_packageName));
          }
        }
      }
    }
  }

  @Nullable
  private static String readPackageName(final char[] text, final Lexer lexer) {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while(true){
      if (lexer.getTokenType() != JavaTokenType.IDENTIFIER && lexer.getTokenType() != JavaTokenType.ASTERISK) {
          break;
        }
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd() - lexer.getTokenStart());

        advanceLexer(lexer);
        if (lexer.getTokenType() != JavaTokenType.DOT) {
          break;
        }
        buffer.append('.');

        advanceLexer(lexer);
      }

      String packageName = buffer.toString();
      if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.') || StringUtil.startsWithChar(packageName, '*') ) {
        return null;
      }
      return packageName;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static void advanceLexer(final Lexer lexer) {
    lexer.advance();
    skipWhiteSpaceAndComments(lexer);
  }
  
  private static void skipWhiteSpaceAndComments(Lexer lexer){
    while(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }


}
