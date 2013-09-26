
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

/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.api.CompilerAPICompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseEmbeddedCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.apache.oro.text.regex.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;
import org.jetbrains.jps.model.serialization.java.compiler.AnnotationProcessorProfileSerializer;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerConfigurationSerializer;

import java.io.File;
import java.util.*;

@State(
  name = "CompilerConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class CompilerConfigurationImpl extends CompilerConfiguration implements PersistentStateComponent<Element>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerConfiguration");
  @NonNls public static final String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  public static final int DEPENDENCY_FORMAT_VERSION = 55;

  @SuppressWarnings({"WeakerAccess"}) public String DEFAULT_COMPILER;
  @NotNull private BackendCompiler myDefaultJavaCompiler;

  // extensions of the files considered as resource files
  private final List<Pattern> myRegexpResourcePatterns = new ArrayList<Pattern>();
  // extensions of the files considered as resource files. If present, overrides patterns in old regexp format stored in myRegexpResourcePatterns
  private final List<String> myWildcardPatterns = new ArrayList<String>();
  private final List<CompiledPattern> myCompiledPatterns = new ArrayList<CompiledPattern>();
  private final List<CompiledPattern> myNegatedCompiledPatterns = new ArrayList<CompiledPattern>();
  private boolean myWildcardPatternsInitialized = false;
  private final Project myProject;
  private final ExcludedEntriesConfiguration myExcludedEntriesConfiguration;

  private final Collection<BackendCompiler> myRegisteredCompilers = new ArrayList<BackendCompiler>();
  private JavacCompiler JAVAC_EXTERNAL_BACKEND;
  private final Perl5Matcher myPatternMatcher = new Perl5Matcher();

  {
    loadDefaultWildcardPatterns();
  }
  private boolean myAddNotNullAssertions = true;

  private final ProcessorConfigProfile myDefaultProcessorsProfile = new ProcessorConfigProfileImpl("Default");
  private final List<ProcessorConfigProfile> myModuleProcessorProfiles = new ArrayList<ProcessorConfigProfile>();

  // the map is calculated by module processor profiles list for faster access to module settings
  private Map<Module, ProcessorConfigProfile> myProcessorsProfilesMap = null;

  @Nullable
  private String myBytecodeTargetLevel = null;  // null means compiler default
  private final Map<String, String> myModuleBytecodeTarget = new java.util.HashMap<String, String>();

  public CompilerConfigurationImpl(Project project) {
    myProject = project;
    myExcludedEntriesConfiguration = new ExcludedEntriesConfiguration();
    Disposer.register(project, myExcludedEntriesConfiguration);
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      public void beforeModuleRemoved(Project project, Module module) {
        getAnnotationProcessingConfiguration(module).removeModuleName(module.getName());
      }

      public void moduleAdded(Project project, Module module) {
        myProcessorsProfilesMap = null; // clear cache
      }
    });

    connection.subscribe(ExternalBuildOptionListener.TOPIC, new ExternalBuildOptionListener() {
      @Override
      public void externalBuildOptionChanged(boolean externalBuildEnabled) {
    // this will schedule for compilation all files that might become compilable after resource patterns' changing
        final TranslatingCompilerFilesMonitor monitor = TranslatingCompilerFilesMonitor.getInstance();
        if (externalBuildEnabled) {
          monitor.suspendProject(myProject);
        }
        else {
          monitor.watchProject(myProject);
          monitor.scanSourcesForCompilableFiles(myProject);
          if (!myProject.isDefault()) {
            final File buildSystem = BuildManager.getInstance().getBuildSystemDirectory();
            final File[] subdirs = buildSystem.listFiles();
            if (subdirs != null) {
              final String prefix = myProject.getName().toLowerCase(Locale.US) + "_";
              for (File subdir : subdirs) {
                if (subdir.getName().startsWith(prefix)) {
                  FileUtil.asyncDelete(subdir);
                }
              }
            }
          }
        }
      }
    });
  }

  public Element getState() {
    try {
      @NonNls final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void setProjectBytecodeTarget(@Nullable String level) {
    myBytecodeTargetLevel = level;
  }

  @Override
  @Nullable
  public String getProjectBytecodeTarget() {
    return myBytecodeTargetLevel;
  }

  public void setModulesBytecodeTargetMap(@NotNull Map<String, String> mapping) {
    myModuleBytecodeTarget.clear();
    myModuleBytecodeTarget.putAll(mapping);
  }

  public Map<String, String> getModulesBytecodeTargetMap() {
    return myModuleBytecodeTarget;
  }

  public void setBytecodeTargetLevel(Module module, String level) {
    final String previous;
    if (StringUtil.isEmpty(level)) {
      previous = myModuleBytecodeTarget.remove(module.getName());
    }
    else {
      previous = myModuleBytecodeTarget.put(module.getName(), level);
    }
    // todo: mark module as dirty in order to rebuild it completely with the new target level
    //if (!Comparing.equal(previous, level)) {
    //  final Project project = module.getProject();
    //
    //}
  }

  @Override
  @Nullable
  public String getBytecodeTargetLevel(Module module) {
    final String level = myModuleBytecodeTarget.get(module.getName());
    if (level != null) {
      return level.isEmpty() ? null : level;
    }
    return myBytecodeTargetLevel;
  }

  private void loadDefaultWildcardPatterns() {
    if (!myWildcardPatterns.isEmpty()) {
      removeWildcardPatterns();
    }
    try {
      addWildcardResourcePattern("!?*.java");
      addWildcardResourcePattern("!?*.form");
      addWildcardResourcePattern("!?*.class");
      addWildcardResourcePattern("!?*.groovy");
      addWildcardResourcePattern("!?*.scala");
      addWildcardResourcePattern("!?*.flex");
      addWildcardResourcePattern("!?*.kt");
      addWildcardResourcePattern("!?*.clj");
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
  }

  public static String getTestsExternalCompilerHome() {
    String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null) {
      if (SystemInfo.isMac) {
        compilerHome = new File(System.getProperty("java.home")).getAbsolutePath();
      }
      else {
        compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();        
      }
    }
    return compilerHome;
  }

  private static Pattern compilePattern(@NonNls String s) throws MalformedPatternException {
    try {
      final PatternCompiler compiler = new Perl5Compiler();
      return SystemInfo.isFileSystemCaseSensitive? compiler.compile(s) : compiler.compile(s, Perl5Compiler.CASE_INSENSITIVE_MASK);
    }
    catch (org.apache.oro.text.regex.MalformedPatternException ex) {
      throw new MalformedPatternException(ex);
    }
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public JavacCompiler getJavacCompiler() {
    createCompilers();
    return JAVAC_EXTERNAL_BACKEND;
  }

  public void projectOpened() {
    createCompilers();
  }

  private void createCompilers() {
    if (JAVAC_EXTERNAL_BACKEND != null) {
      return;
    }

    JAVAC_EXTERNAL_BACKEND = new JavacCompiler(myProject);
    myRegisteredCompilers.add(JAVAC_EXTERNAL_BACKEND);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      //final BackendCompiler JIKES_BACKEND = new JikesCompiler(myProject);
      //myRegisteredCompilers.add(JIKES_BACKEND);

      if (EclipseCompiler.isInitialized()) {
        final EclipseCompiler eclipse = new EclipseCompiler(myProject);
        myRegisteredCompilers.add(eclipse);
      }

      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        try {
          final EclipseEmbeddedCompiler eclipseEmbedded = new EclipseEmbeddedCompiler(myProject);
          myRegisteredCompilers.add(eclipseEmbedded);
        }
        catch (NoClassDefFoundError e) {
          // eclipse jar must be not in the classpath
        }
        try {
          CompilerAPICompiler inProcessJavaCompiler = new CompilerAPICompiler(myProject);
          myRegisteredCompilers.add(inProcessJavaCompiler);
        }
        catch (NoClassDefFoundError e) {
          // wrong JDK
        }
      }
    }

    final BackendCompiler[] compilers = Extensions.getExtensions(BackendCompiler.EP_NAME, myProject);
    final Set<FileType> types = new HashSet<FileType>();
    for (BackendCompiler compiler : compilers) {
      myRegisteredCompilers.add(compiler);
      types.addAll(compiler.getCompilableFileTypes());
    }

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    for (FileType type : types) {
      compilerManager.addCompilableFileType(type);
    }

    myDefaultJavaCompiler = JAVAC_EXTERNAL_BACKEND;
    for (BackendCompiler compiler : myRegisteredCompilers) {
      if (compiler.getId().equals(DEFAULT_COMPILER)) {
        myDefaultJavaCompiler = compiler;
        break;
      }
    }
    DEFAULT_COMPILER = myDefaultJavaCompiler.getId();
  }

  public Collection<BackendCompiler> getRegisteredJavaCompilers() {
    createCompilers();
    return myRegisteredCompilers;
  }

  public String[] getResourceFilePatterns() {
    return getWildcardPatterns();
  }

  private String[] getRegexpPatterns() {
    String[] patterns = ArrayUtil.newStringArray(myRegexpResourcePatterns.size());
    int index = 0;
    for (final Pattern myRegexpResourcePattern : myRegexpResourcePatterns) {
      patterns[index++] = myRegexpResourcePattern.getPattern();
    }
    return patterns;
  }

  private String[] getWildcardPatterns() {
    return ArrayUtil.toStringArray(myWildcardPatterns);
  }

  public void addResourceFilePattern(String namePattern) throws MalformedPatternException {
    addWildcardResourcePattern(namePattern);
  }

  // need this method only for handling patterns in old regexp format
  private void addRegexpPattern(String namePattern) throws MalformedPatternException {
    Pattern pattern = compilePattern(namePattern);
    if (pattern != null) {
      myRegexpResourcePatterns.add(pattern);
    }
  }

  @Override
  public ExcludedEntriesConfiguration getExcludedEntriesConfiguration() {
    return myExcludedEntriesConfiguration;
  }

  public boolean isExcludedFromCompilation(final VirtualFile virtualFile) {
    return myExcludedEntriesConfiguration.isExcluded(virtualFile);
  }

  @Override
  public boolean isResourceFile(VirtualFile virtualFile) {
    return isResourceFile(virtualFile.getName(), virtualFile.getParent());
  }

  @Override
  public boolean isAddNotNullAssertions() {
    return myAddNotNullAssertions;
  }

  @Override
  public void setAddNotNullAssertions(boolean enabled) {
    myAddNotNullAssertions = enabled;
  }

  @NotNull
  public ProcessorConfigProfile getDefaultProcessorProfile() {
    return myDefaultProcessorsProfile;
  }

  public void setDefaultProcessorProfile(ProcessorConfigProfile profile) {
    myDefaultProcessorsProfile.initFrom(profile);
  }

  @NotNull
  public List<ProcessorConfigProfile> getModuleProcessorProfiles() {
    return myModuleProcessorProfiles;
  }

  public void setModuleProcessorProfiles(Collection<ProcessorConfigProfile> moduleProfiles) {
    myModuleProcessorProfiles.clear();
    myModuleProcessorProfiles.addAll(moduleProfiles);
    myProcessorsProfilesMap = null;
  }

  @Nullable
  public ProcessorConfigProfile findModuleProcessorProfile(@NotNull String name) {
    for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
      if (name.equals(profile.getName())) {
        return profile;
      }
    }

    return null;
  }

  public void removeModuleProcessorProfile(ProcessorConfigProfile profile) {
    myModuleProcessorProfiles.remove(profile);
    myProcessorsProfilesMap = null; // clear cache
  }

  public void addModuleProcessorProfile(@NotNull ProcessorConfigProfile profile) {
    myModuleProcessorProfiles.add(profile);
    myProcessorsProfilesMap = null; // clear cache
  }

  @Override
  @NotNull
  public ProcessorConfigProfile getAnnotationProcessingConfiguration(Module module) {
    Map<Module, ProcessorConfigProfile> map = myProcessorsProfilesMap;
    if (map == null) {
      map = new HashMap<Module, ProcessorConfigProfile>();
      final Map<String, Module> namesMap = new HashMap<String, Module>();
      for (Module m : ModuleManager.getInstance(module.getProject()).getModules()) {
        namesMap.put(m.getName(), m);
      }
      if (!namesMap.isEmpty()) {
        for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
          for (String name : profile.getModuleNames()) {
            final Module mod = namesMap.get(name);
            if (mod != null) {
              map.put(mod, profile);
            }
          }
        }
      }
      myProcessorsProfilesMap = map;
    }
    final ProcessorConfigProfile profile = map.get(module);
    return profile != null? profile : myDefaultProcessorsProfile;
  }

  @Override
  public boolean isAnnotationProcessorsEnabled() {
    if (myDefaultProcessorsProfile.isEnabled()) {
      return true;
    }
    for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
      if (profile.isEnabled()) {
        return true;
      }
    }
    return false;
  }

  private void addWildcardResourcePattern(@NonNls final String wildcardPattern) throws MalformedPatternException {
    final CompiledPattern pattern = convertToRegexp(wildcardPattern);
    if (pattern != null) {
      myWildcardPatterns.add(wildcardPattern);
      if (isPatternNegated(wildcardPattern)) {
        myNegatedCompiledPatterns.add(pattern);
      }
      else {
        myCompiledPatterns.add(pattern);
      }
    }
  }

  public void removeResourceFilePatterns() {
    removeWildcardPatterns();
  }

  private void removeRegexpPatterns() {
    myRegexpResourcePatterns.clear();
  }

  private void removeWildcardPatterns() {
    myWildcardPatterns.clear();
    myCompiledPatterns.clear();
    myNegatedCompiledPatterns.clear();
  }

  private static CompiledPattern convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }

    wildcardPattern = FileUtil.toSystemIndependentName(wildcardPattern);

    String srcRoot = null;
    int colon = wildcardPattern.indexOf(":");
    if (colon > 0) {
      srcRoot = wildcardPattern.substring(0, colon);
      wildcardPattern = wildcardPattern.substring(colon + 1);
    }

    String dirPattern = null;
    int slash = wildcardPattern.lastIndexOf('/');
    if (slash >= 0) {
      dirPattern = wildcardPattern.substring(0, slash + 1);
      wildcardPattern = wildcardPattern.substring(slash + 1);
      if (!dirPattern.startsWith("/")) {
        dirPattern = "/" + dirPattern;
      }
      //now dirPattern starts and ends with '/'

      dirPattern = normalizeWildcards(dirPattern);

      dirPattern = StringUtil.replace(dirPattern, "/.*.*/", "(/.*)?/");
      dirPattern = StringUtil.trimEnd(dirPattern, "/");

      dirPattern = optimize(dirPattern);
    }

    wildcardPattern = normalizeWildcards(wildcardPattern);
    wildcardPattern = optimize(wildcardPattern);

    final Pattern dirCompiled = dirPattern == null ? null : compilePattern(dirPattern);
    final Pattern srcCompiled = srcRoot == null ? null : compilePattern(optimize(normalizeWildcards(srcRoot)));
    return new CompiledPattern(compilePattern(wildcardPattern), dirCompiled, srcCompiled);
  }

  private static String optimize(String wildcardPattern) {
    return wildcardPattern.replaceAll("(?:\\.\\*)+", ".*");
  }

  private static String normalizeWildcards(String wildcardPattern) {
    wildcardPattern = StringUtil.replace(wildcardPattern, "\\!", "!");
    wildcardPattern = StringUtil.replace(wildcardPattern, ".", "\\.");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*?", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?*", ".+");
    wildcardPattern = StringUtil.replace(wildcardPattern, "*", ".*");
    wildcardPattern = StringUtil.replace(wildcardPattern, "?", ".");
    return wildcardPattern;
  }

  public static boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  public boolean isResourceFile(String name) {
    return isResourceFile(name, null);
  }

  private boolean matches(String s, Pattern p) {
    synchronized (myPatternMatcher) {
      try {
        return myPatternMatcher.matches(s, p);
      }
      catch (Exception e) {
        LOG.error("Exception matching file name \"" + s + "\" against the pattern \"" + p + "\"", e);
        return false;
      }
    }
  }

  private boolean isResourceFile(String name, @Nullable VirtualFile parent) {
    final Ref<String> parentRef = Ref.create(null);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myCompiledPatterns.size(); i++) {
      if (matches(name, parent, parentRef, myCompiledPatterns.get(i))) {
        return true;
      }
    }

    if (myNegatedCompiledPatterns.isEmpty()) {
      return false;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myNegatedCompiledPatterns.size(); i++) {
      if (matches(name, parent, parentRef, myNegatedCompiledPatterns.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean matches(String name, VirtualFile parent, Ref<String> parentRef, CompiledPattern pair) {
    if (!matches(name, pair.fileName)) {
      return false;
    }

    if (parent != null && (pair.dir != null || pair.srcRoot != null)) {
      VirtualFile srcRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getSourceRootForFile(parent);
      if (pair.dir != null) {
        String parentPath = parentRef.get();
        if (parentPath == null) {
          parentRef.set(parentPath = srcRoot == null ? parent.getPath() : VfsUtilCore.getRelativePath(parent, srcRoot, '/'));
        }
        if (parentPath == null || !matches("/" + parentPath, pair.dir)) {
          return false;
        }
      }

      if (pair.srcRoot != null) {
        String srcRootName = srcRoot == null ? null : srcRoot.getName();
        if (srcRootName == null || !matches(srcRootName, pair.srcRoot)) {
          return false;
        }
      }
    }

    return true;
  }


  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);

    final Element notNullAssertions = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.ADD_NOTNULL_ASSERTIONS);
    if (notNullAssertions != null) {
      myAddNotNullAssertions = Boolean.valueOf(notNullAssertions.getAttributeValue(JpsJavaCompilerConfigurationSerializer.ENABLED, "true"));
    }

    Element node = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.EXCLUDE_FROM_COMPILE);
    if (node != null) {
      myExcludedEntriesConfiguration.readExternal(node);
    }

    try {
      removeRegexpPatterns();
      node = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.RESOURCE_EXTENSIONS);
      if (node != null) {
        for (final Object o : node.getChildren(JpsJavaCompilerConfigurationSerializer.ENTRY)) {
          Element element = (Element)o;
          String pattern = element.getAttributeValue(JpsJavaCompilerConfigurationSerializer.NAME);
          if (!StringUtil.isEmpty(pattern)) {
            addRegexpPattern(pattern);
          }
        }
      }

      removeWildcardPatterns();
      node = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.WILDCARD_RESOURCE_PATTERNS);
      if (node != null) {
        myWildcardPatternsInitialized = true;
        for (final Object o : node.getChildren(JpsJavaCompilerConfigurationSerializer.ENTRY)) {
          final Element element = (Element)o;
          String pattern = element.getAttributeValue(JpsJavaCompilerConfigurationSerializer.NAME);
          if (!StringUtil.isEmpty(pattern)) {
            addWildcardResourcePattern(pattern);
          }
        }
      }
    }
    catch (MalformedPatternException e) {
      throw new InvalidDataException(e);
    }


    myModuleProcessorProfiles.clear();
    myProcessorsProfilesMap = null;

    final Element annotationProcessingSettings = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.ANNOTATION_PROCESSING);
    if (annotationProcessingSettings != null) {
      final List profiles = annotationProcessingSettings.getChildren("profile");
      if (!profiles.isEmpty()) {
        for (Object elem : profiles) {
          final Element profileElement = (Element)elem;
          final boolean isDefault = "true".equals(profileElement.getAttributeValue("default"));
          if (isDefault) {
            AnnotationProcessorProfileSerializer.readExternal(myDefaultProcessorsProfile, profileElement);
          }
          else {
            final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl("");
            AnnotationProcessorProfileSerializer.readExternal(profile, profileElement);
            myModuleProcessorProfiles.add(profile);
          }
        }
      }
      else {
        // assuming older format
        loadProfilesFromOldFormat(annotationProcessingSettings);
      }
    }

    myBytecodeTargetLevel = null;
    myModuleBytecodeTarget.clear();
    final Element bytecodeTargetElement = parentNode.getChild(JpsJavaCompilerConfigurationSerializer.BYTECODE_TARGET_LEVEL);
    if (bytecodeTargetElement != null) {
      myBytecodeTargetLevel = bytecodeTargetElement.getAttributeValue(JpsJavaCompilerConfigurationSerializer.TARGET_ATTRIBUTE);
      for (Element elem : (Collection<Element>)bytecodeTargetElement.getChildren(JpsJavaCompilerConfigurationSerializer.MODULE)) {
        final String name = elem.getAttributeValue(JpsJavaCompilerConfigurationSerializer.NAME);
        if (name == null) {
          continue;
        }
        final String target = elem.getAttributeValue(JpsJavaCompilerConfigurationSerializer.TARGET_ATTRIBUTE);
        if (target == null) {
          continue;
        }
        myModuleBytecodeTarget.put(name, target);
      }
    }
  }

  private void loadProfilesFromOldFormat(Element processing) {
    // collect data
    final boolean isEnabled = Boolean.parseBoolean(processing.getAttributeValue(JpsJavaCompilerConfigurationSerializer.ENABLED, "false"));
    final boolean isUseClasspath = Boolean.parseBoolean(processing.getAttributeValue("useClasspath", "true"));
    final StringBuilder processorPath = new StringBuilder();
    final Set<String> optionPairs = new HashSet<String>();
    final Set<String> processors = new HashSet<String>();
    final List<Pair<String, String>> modulesToProcess = new ArrayList<Pair<String, String>>();

    for (Object child : processing.getChildren("processorPath")) {
      final Element pathElement = (Element)child;
      final String path = pathElement.getAttributeValue("value", (String)null);
      if (path != null) {
        if (processorPath.length() > 0) {
          processorPath.append(File.pathSeparator);
        }
        processorPath.append(path);
      }
    }

    for (Object child : processing.getChildren("processor")) {
      final Element processorElement = (Element)child;
      final String proc = processorElement.getAttributeValue(JpsJavaCompilerConfigurationSerializer.NAME, (String)null);
      if (proc != null) {
        processors.add(proc);
      }
      final StringTokenizer tokenizer = new StringTokenizer(processorElement.getAttributeValue("options", ""), " ", false);
      while (tokenizer.hasMoreTokens()) {
        final String pair = tokenizer.nextToken();
        optionPairs.add(pair);
      }
    }

    for (Object child : processing.getChildren("processModule")) {
      final Element moduleElement = (Element)child;
      final String name = moduleElement.getAttributeValue(JpsJavaCompilerConfigurationSerializer.NAME, (String)null);
      if (name == null) {
        continue;
      }
      final String dir = moduleElement.getAttributeValue("generatedDirName", (String)null);
      modulesToProcess.add(Pair.create(name, dir));
    }

    myDefaultProcessorsProfile.setEnabled(false);
    myDefaultProcessorsProfile.setObtainProcessorsFromClasspath(isUseClasspath);
    if (processorPath.length() > 0) {
      myDefaultProcessorsProfile.setProcessorPath(processorPath.toString());
    }
    if (!optionPairs.isEmpty()) {
      for (String pair : optionPairs) {
        final int index = pair.indexOf("=");
        if (index > 0) {
          myDefaultProcessorsProfile.setOption(pair.substring(0, index), pair.substring(index + 1));
        }
      }
    }
    for (String processor : processors) {
      myDefaultProcessorsProfile.addProcessor(processor);
    }

    final Map<String, Set<String>> dirNameToModulesMap = new HashMap<String, Set<String>>();
    for (Pair<String, String> moduleDirPair : modulesToProcess) {
      final String dir = moduleDirPair.getSecond();
      Set<String> set = dirNameToModulesMap.get(dir);
      if (set == null) {
        set = new HashSet<String>();
        dirNameToModulesMap.put(dir, set);
      }
      set.add(moduleDirPair.getFirst());
    }

    int profileIndex = 0;
    for (Map.Entry<String, Set<String>> entry : dirNameToModulesMap.entrySet()) {
      final String dirName = entry.getKey();
      final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl(myDefaultProcessorsProfile);
      profile.setName("Profile" + (++profileIndex));
      profile.setEnabled(isEnabled);
      profile.setGeneratedSourcesDirectoryName(dirName, false);
      for (String moduleName : entry.getValue()) {
        profile.addModuleName(moduleName);
      }
      myModuleProcessorProfiles.add(profile);
    }
  }

  private void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);

    if (myAddNotNullAssertions != true) {
      addChild(parentNode, JpsJavaCompilerConfigurationSerializer.ADD_NOTNULL_ASSERTIONS).setAttribute(
        JpsJavaCompilerConfigurationSerializer.ENABLED, String.valueOf(myAddNotNullAssertions));
    }

    if(myExcludedEntriesConfiguration.getExcludeEntryDescriptions().length > 0) {
      myExcludedEntriesConfiguration.writeExternal(addChild(parentNode, JpsJavaCompilerConfigurationSerializer.EXCLUDE_FROM_COMPILE));
    }

    final Element newChild = addChild(parentNode, JpsJavaCompilerConfigurationSerializer.RESOURCE_EXTENSIONS);
    for (final String pattern : getRegexpPatterns()) {
      addChild(newChild, JpsJavaCompilerConfigurationSerializer.ENTRY).setAttribute(JpsJavaCompilerConfigurationSerializer.NAME, pattern);
    }

    if (myWildcardPatternsInitialized || !myWildcardPatterns.isEmpty()) {
      final Element wildcardPatterns = addChild(parentNode, JpsJavaCompilerConfigurationSerializer.WILDCARD_RESOURCE_PATTERNS);
      for (final String wildcardPattern : myWildcardPatterns) {
        addChild(wildcardPatterns, JpsJavaCompilerConfigurationSerializer.ENTRY).setAttribute(JpsJavaCompilerConfigurationSerializer.NAME, wildcardPattern);
      }
    }

    final Element annotationProcessingSettings = addChild(parentNode, JpsJavaCompilerConfigurationSerializer.ANNOTATION_PROCESSING);
    final Element defaultProfileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "true");
    AnnotationProcessorProfileSerializer.writeExternal(myDefaultProcessorsProfile, defaultProfileElem);
    for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
      final Element profileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "false");
      AnnotationProcessorProfileSerializer.writeExternal(profile, profileElem);
    }

    if (!StringUtil.isEmpty(myBytecodeTargetLevel) || !myModuleBytecodeTarget.isEmpty()) {
      final Element bytecodeTarget = addChild(parentNode, JpsJavaCompilerConfigurationSerializer.BYTECODE_TARGET_LEVEL);
      if (!StringUtil.isEmpty(myBytecodeTargetLevel)) {
        bytecodeTarget.setAttribute(JpsJavaCompilerConfigurationSerializer.TARGET_ATTRIBUTE, myBytecodeTargetLevel);
      }
      if (!myModuleBytecodeTarget.isEmpty()) {
        final List<String> moduleNames = new ArrayList<String>(myModuleBytecodeTarget.keySet());
        Collections.sort(moduleNames, String.CASE_INSENSITIVE_ORDER);
        for (String name : moduleNames) {
          final Element moduleElement = addChild(bytecodeTarget, JpsJavaCompilerConfigurationSerializer.MODULE);
          moduleElement.setAttribute(JpsJavaCompilerConfigurationSerializer.NAME, name);
          final String value = myModuleBytecodeTarget.get(name);
          moduleElement.setAttribute(JpsJavaCompilerConfigurationSerializer.TARGET_ATTRIBUTE, value != null? value : "");
        }
      }
    }
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "CompilerConfiguration";
  }

  public BackendCompiler getDefaultCompiler() {
    createCompilers();
    return myDefaultJavaCompiler;
  }

  /**
   * @param defaultCompiler The compiler that is passed as a parameter to setDefaultCompiler() 
   * must be one of the registered compilers in compiler configuration.
   * Otherwise because of lazy compiler initialization, the value of default compiler will point to some other compiler instance
   */
  public void setDefaultCompiler(BackendCompiler defaultCompiler) {
    myDefaultJavaCompiler = defaultCompiler;
    DEFAULT_COMPILER = defaultCompiler.getId();
  }

  public void convertPatterns() {
    if (!needPatternConversion()) {
      return;
    }
    try {
      boolean ok;
      try {
        ok = doConvertPatterns();
      }
      catch (MalformedPatternException e) {
        ok = false;
      }
      if (!ok) {
        final String initialPatternString = patternsToString(getRegexpPatterns());
        final String message = CompilerBundle.message(
          "message.resource.patterns.format.changed",
          ApplicationNamesInfo.getInstance().getProductName(),
          initialPatternString,
          CommonBundle.getOkButtonText(),
          CommonBundle.getCancelButtonText()
        );
        final String wildcardPatterns = Messages.showInputDialog(
          myProject, message, CompilerBundle.message("pattern.conversion.dialog.title"), Messages.getWarningIcon(), initialPatternString, new InputValidator() {
          public boolean checkInput(String inputString) {
            return true;
          }
          public boolean canClose(String inputString) {
            final StringTokenizer tokenizer = new StringTokenizer(inputString, ";", false);
            StringBuilder malformedPatterns = new StringBuilder();

            while (tokenizer.hasMoreTokens()) {
              String pattern = tokenizer.nextToken();
              try {
                addWildcardResourcePattern(pattern);
              }
              catch (MalformedPatternException e) {
                malformedPatterns.append("\n\n");
                malformedPatterns.append(pattern);
                malformedPatterns.append(": ");
                malformedPatterns.append(e.getMessage());
              }
            }

            if (malformedPatterns.length() > 0) {
              Messages.showErrorDialog(CompilerBundle.message("error.bad.resource.patterns", malformedPatterns.toString()),
                                       CompilerBundle.message("bad.resource.patterns.dialog.title"));
              removeWildcardPatterns();
              return false;
            }
            return true;
          }
        });
        if (wildcardPatterns == null) { // cancel pressed
          loadDefaultWildcardPatterns();
        }
      }
    }
    finally {
      myWildcardPatternsInitialized = true;
    }
  }

  private boolean needPatternConversion() {
    return !myWildcardPatternsInitialized && !myRegexpResourcePatterns.isEmpty();
  }

  private boolean doConvertPatterns() throws MalformedPatternException {
    final String[] regexpPatterns = getRegexpPatterns();
    final List<String> converted = new ArrayList<String>();
    final Pattern multipleExtensionsPatternPattern = compilePattern("\\.\\+\\\\\\.\\((\\w+(?:\\|\\w+)*)\\)");
    final Pattern singleExtensionPatternPattern = compilePattern("\\.\\+\\\\\\.(\\w+)");
    final Perl5Matcher matcher = new Perl5Matcher();
    for (final String regexpPattern : regexpPatterns) {
      //final Matcher multipleExtensionsMatcher = multipleExtensionsPatternPattern.matcher(regexpPattern);
      if (matcher.matches(regexpPattern, multipleExtensionsPatternPattern)) {
        final MatchResult match = matcher.getMatch();
        final StringTokenizer tokenizer = new StringTokenizer(match.group(1), "|", false);
        while (tokenizer.hasMoreTokens()) {
          converted.add("?*." + tokenizer.nextToken());
        }
      }
      else {
        //final Matcher singleExtensionMatcher = singleExtensionPatternPattern.matcher(regexpPattern);
        if (matcher.matches(regexpPattern, singleExtensionPatternPattern)) {
          final MatchResult match = matcher.getMatch();
          converted.add("?*." + match.group(1));
        }
        else {
          return false;
        }
      }
    }
    for (final String aConverted : converted) {
      addWildcardResourcePattern(aConverted);
    }
    return true;
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuilder extensionsString = new StringBuilder();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }
  
  private static class CompiledPattern {
    @NotNull final Pattern fileName;
    @Nullable final Pattern dir;
    @Nullable final Pattern srcRoot;

    private CompiledPattern(Pattern fileName, Pattern dir, Pattern srcRoot) {
      this.fileName = fileName;
      this.dir = dir;
      this.srcRoot = srcRoot;
    }
  }

  private static Element addChild(Element parent, final String childName) {
    final Element child = new Element(childName);
    parent.addContent(child);
    return child;
  }

}