package org.jetbrains.jps.incremental.java;

import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToFormMapping;
import org.jetbrains.jps.javac.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends Builder{
  public static final String BUILDER_NAME = "java";

  private static final String JAVA_EXTENSION = ".java";
  private static final String FORM_EXTENSION = ".form";

  private static final FileFilter JAVA_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getPath().endsWith(JAVA_EXTENSION);
    }
  };
  private static final FileFilter FORM_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getPath().endsWith(FORM_EXTENSION);
    }
  };

  private static final Key<Callbacks.Backend> DELTA_MAPPINGS_CALLBACK_KEY = Key.create("_dependency_data_");
  private final ExecutorService myTaskRunner;
  private final List<ClassPostProcessor> myClassProcessors = new ArrayList<ClassPostProcessor>();

  public JavaBuilder(ExecutorService tasksExecutor) {
    myTaskRunner = tasksExecutor;
    //add here class processors in the sequence they should be executed
    myClassProcessors.add(new ClassPostProcessor() {
      public void process(CompileContext context, OutputFileObject out) {
        final Callbacks.Backend callback = DELTA_MAPPINGS_CALLBACK_KEY.get(context);
        if (callback != null) {
          final OutputFileObject.Content content = out.getContent();
          final File srcFile = out.getSourceFile();
          if (srcFile != null && content != null) {
            final String outputPath = FileUtil.toSystemIndependentName(out.getFile().getPath());
            final String sourcePath = FileUtil.toSystemIndependentName(srcFile.getPath());
            final RootDescriptor moduleAndRoot = context.getModuleAndRoot(srcFile);
            final BuildDataManager dataManager = context.getDataManager();
            if (moduleAndRoot != null) {
              try {
                final String moduleName = moduleAndRoot.module.getName().toLowerCase(Locale.US);
                dataManager.getSourceToOutputMap(moduleName, context.isCompilingTests()).appendData(sourcePath, outputPath);
              }
              catch (Exception e) {
                context.processMessage(new CompilerMessage(BUILDER_NAME, e));
              }
            }
            final ClassReader reader = new ClassReader(content.getBuffer(), content.getOffset(), content.getLength());
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (dataManager.getMappings()) {
              callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), reader);
            }
          }
        }
      }
    });
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  public String getDescription() {
    return "Java Builder";
  }

  private static final Key<Set<File>> TEMPORARY_SOURCE_ROOTS_KEY = Key.create("_additional_source_roots_");

  public static void addTempSourcePathRoot(CompileContext context, File root) {
    Set<File> roots = TEMPORARY_SOURCE_ROOTS_KEY.get(context);
    if (roots == null) {
      roots = new HashSet<File>();
      TEMPORARY_SOURCE_ROOTS_KEY.set(context, roots);
    }
    roots.add(root);
  }

  public ExitCode build(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    try {
      final Map<File, Module> filesToCompile = new HashMap<File, Module>();
      final List<File> formsToCompile = new ArrayList<File>();

      context.processFilesToRecompile(chunk, new FileProcessor() {
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          if (JAVA_SOURCES_FILTER.accept(file)) {
            filesToCompile.put(file, module);
          }
          else if (FORM_SOURCES_FILTER.accept(file)) {
            formsToCompile.add(file);
          }
          return true;
        }
      });

      // force compilation of bound source file if the form is dirty
      for (File form : formsToCompile) {
        final RootDescriptor descriptor = context.getModuleAndRoot(form);
        if (descriptor != null) {
          for (RootDescriptor rd : context.getModuleRoots(descriptor.module)) {
            final File boundSource = getBoundSource(rd.root, form);
            if (boundSource != null) {
              filesToCompile.put(boundSource, rd.module);
              break;
            }
          }
        }
      }

      // form should be considered dirty if the class it is bound to is dirty
      final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();
      for (File srcFile : filesToCompile.keySet()) {
        final String srcPath = srcFile.getPath();
        final String formPath = sourceToFormMap.getState(srcPath);
        if (formPath != null) {
          final File formFile = new File(formPath);
          if (formFile.exists()) {
            context.markDirty(formFile);
            formsToCompile.add(formFile);
          }
          sourceToFormMap.remove(srcPath);
        }
      }

      deleteCorrespondingOutputFiles(context, filesToCompile);

      return compile(context, chunk, filesToCompile.keySet(), formsToCompile);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (message == null) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        message = "Internal error: \n" + out.toString();
      }
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      throw new ProjectBuildException(message, e);
    }
  }

  @Nullable
  private static File getBoundSource(File srcRoot, File formFile) throws IOException {
    final String boundClassName = FormsParsing.readBoundClassName(formFile);
    if (boundClassName == null) {
      return null;
    }
    String relPath = boundClassName.replace('.', '/') + JAVA_EXTENSION;
    while (true) {
      final File candidate = new File(srcRoot, relPath);
      if (candidate.exists()) {
        return candidate.isFile()? candidate : null;
      }
      final int index = relPath.lastIndexOf('/');
      if (index <= 0) {
        return null;
      }
      relPath = relPath.substring(0, index) + JAVA_EXTENSION;
    }
  }

  private ExitCode compile(final CompileContext context, ModuleChunk chunk, Collection<File> files, Collection<File> forms) throws Exception {
    ExitCode exitCode = ExitCode.OK;

    final boolean hasSourcesToCompile = !files.isEmpty() || !forms.isEmpty();

    if (!hasSourcesToCompile && !context.hasRemovedSources()) {
      return exitCode;
    }

    final ProjectPaths paths = context.getProjectPaths();

    final boolean addNotNullAssertions = context.getProject().getCompilerConfiguration().isAddNotNullAssertions();

    final Collection<File> classpath = paths.getCompilationClasspath(chunk, context.isCompilingTests(), false/*context.isProjectRebuild()*/);
    final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, context.isCompilingTests(), false/*context.isProjectRebuild()*/);
    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk);

    // begin compilation round
    final DiagnosticSink diagnosticSink = new DiagnosticSink(context);
    final OutputFilesSink outputSink = new OutputFilesSink(context);
    final Mappings delta = context.createDelta();
    DELTA_MAPPINGS_CALLBACK_KEY.set(context, delta.getCallback());
    try {
      if (hasSourcesToCompile) {
        final Set<File> sourcePath = TEMPORARY_SOURCE_ROOTS_KEY.get(context,Collections.<File>emptySet());

        final boolean compiledOk = compileJava(
          options, files, classpath, platformCp, sourcePath, outs, context, diagnosticSink, outputSink
        );

        final Map<File, String> chunkSourcePath = ProjectPaths.getSourceRootsWithDependents(chunk, context.isCompilingTests());
        final ClassLoader compiledClassesLoader = createInstrumentationClassLoader(classpath, platformCp, chunkSourcePath, outputSink);

        if (!forms.isEmpty()) {
          try {
            context.processMessage(new ProgressMessage("Instrumenting forms [" + chunk.getName() + "]"));
            instrumentForms(context, chunk, chunkSourcePath, compiledClassesLoader, forms, outputSink);
          }
          finally {
            context.processMessage(new ProgressMessage("Finished instrumenting forms [" + chunk.getName() + "]"));
          }
        }

        if (addNotNullAssertions) {
          try {
            context.processMessage(new ProgressMessage("Adding NotNull assertions [" + chunk.getName() + "]"));
            instrumentNotNull(context, outputSink, compiledClassesLoader);
          }
          finally {
            context.processMessage(new ProgressMessage("Finished adding NotNull assertions [" + chunk.getName() + "]"));
          }
        }

        if (!compiledOk && diagnosticSink.getErrorCount() == 0) {
          throw new ProjectBuildException("Compilation failed: internal java compiler error");
        }
        if (diagnosticSink.getErrorCount() > 0) {
          throw new ProjectBuildException("Compilation failed: errors: " + diagnosticSink.getErrorCount() + "; warnings: " + diagnosticSink.getWarningCount());
        }
      }
    }
    finally {
      outputSink.writePendingData();

      final Set<File> successfullyCompiled = outputSink.getSuccessfullyCompiled();
      DELTA_MAPPINGS_CALLBACK_KEY.set(context, null);

      if (updateMappings(context, delta, chunk, files, successfullyCompiled)) {
        exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
    }

    if (exitCode != ExitCode.ADDITIONAL_PASS_REQUIRED) {
      final Set<File> tempRoots = TEMPORARY_SOURCE_ROOTS_KEY.get(context);
      TEMPORARY_SOURCE_ROOTS_KEY.set(context, null);
      if (tempRoots != null) {
        for (File root : tempRoots) {
          FileUtil.delete(root);
        }
      }
    }
    return exitCode;
  }

  private boolean compileJava(List<String> options, Collection<File> files, Collection<File> classpath, Collection<File> platformCp, Collection<File> sourcePath, Map<File, Set<File>> outs, CompileContext context, DiagnosticOutputConsumer diagnosticSink, final OutputFileConsumer outputSink) {
    final ClassProcessingConsumer classesConsumer = new ClassProcessingConsumer(context, outputSink);
    try {
      final JavacProxy proxy = createJavacProxy(context);
      return proxy.compile(options, files, classpath, platformCp, sourcePath, outs, diagnosticSink, classesConsumer);
    }
    finally {
      classesConsumer.ensurePendingTasksCompleted();
    }
  }

  private JavacProxy createJavacProxy(CompileContext context) {
    return new EmbeddedJavacProxy(context.getCancelStatus());
  }

  private static ClassLoader createInstrumentationClassLoader(Collection<File> classpath, Collection<File> platformCp, Map<File, String> chunkSourcePath, OutputFilesSink outputSink)
    throws MalformedURLException {
    final List<URL> urls = new ArrayList<URL>();
    for (Collection<File> cp : Arrays.asList(platformCp, classpath)) {
      for (File file : cp) {
        urls.add(file.toURI().toURL());
      }
    }
    urls.add(getResourcePath(GridConstraints.class).toURI().toURL()); // forms_rt.jar
    //urls.add(getResourcePath(CellConstraints.class).toURI().toURL());  // jgoodies-forms
    for (File file : chunkSourcePath.keySet()) {
      urls.add(file.toURI().toURL());
    }
    return new CompiledClassesLoader(outputSink, urls.toArray(new URL[urls.size()]));
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk) {
    final List<String> options = new ArrayList<String>();
    options.add("-verbose");

    final Project project = context.getProject();
    final Map<String, String> javacOpts = project.getCompilerConfiguration().getJavacOptions();
    final boolean debugInfo = !"false".equals(javacOpts.get("DEBUGGING_INFO"));
    final boolean nowarn = "true".equals(javacOpts.get("GENERATE_NO_WARNINGS"));
    final boolean deprecation = !"false".equals(javacOpts.get("DEPRECATION"));
    if (debugInfo) {
      options.add("-g");
    }
    if (deprecation) {
      options.add("-deprecation");
    }
    if (nowarn) {
      options.add("-nowarn");
    }

    final String customArgs = javacOpts.get("ADDITIONAL_OPTIONS_STRING");
    boolean isEncodingSet = false;
    if (customArgs != null) {
      final StringTokenizer tokenizer = new StringTokenizer(customArgs, " \t\r\n");
      while(tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();
        if ("-g".equals(token) || "-deprecation".equals(token) || "-nowarn".equals(token) || "-verbose".equals(token)){
          continue;
        }
        options.add(token);
        if ("-encoding".equals(token)) {
          isEncodingSet = true;
        }
      }
    }

    if (!isEncodingSet && project.getProjectCharset() != null) {
      options.add("-encoding");
      options.add(project.getProjectCharset());
    }
    return options;
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new HashMap<File, Set<File>>();
    final boolean compilingTests = context.isCompilingTests();
    for (Module module : chunk.getModules()) {
      final String outputPath;
      final Collection<String> srcPaths;
      if (compilingTests) {
        outputPath = module.getTestOutputPath();
        srcPaths = module.getTestRoots();
      }
      else {
        outputPath = module.getOutputPath();
        srcPaths = module.getSourceRoots();
      }
      final Set<File> roots = new HashSet<File>();
      for (String path : srcPaths) {
        roots.add(new File(path));
      }
      map.put(new File(outputPath), roots);
    }
    return map;
  }

  // todo: probably instrument other NotNull-like annotations defined in project settings?
  private static void instrumentNotNull(CompileContext context, OutputFilesSink sink, final ClassLoader loader) {
    for (final OutputFileObject fileObject : sink.getFileObjects()) {
      final OutputFileObject.Content originalContent = fileObject.getContent();
      final ClassReader reader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());
      final int version = getClassFileVersion(reader);
      if (version >= Opcodes.V1_5) {
        boolean success = false;
        final ClassWriter writer = new InstrumenterClassWriter(getAsmClassWriterFlags(version), loader);
        try {
          final NotNullVerifyingInstrumenter instrumenter = new NotNullVerifyingInstrumenter(writer);
          reader.accept(instrumenter, 0);
          if (instrumenter.isModification()) {
            fileObject.updateContent(writer.toByteArray());
          }
          success = true;
        }
        catch (Throwable e) {
          final StringBuilder msg = new StringBuilder();
          msg.append("@NotNull instrumentation failed ");
          final File sourceFile = fileObject.getSourceFile();
          if (sourceFile != null) {
            msg.append(" for ").append(sourceFile.getName());
          }
          msg.append(": ").append(e.getMessage());
          context.processMessage(
            new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, msg.toString(), sourceFile != null ? sourceFile.getPath() : null));
        }
        finally {
          if (!success) {
            sink.markError(fileObject);
          }
        }
      }
    }
  }

  private static void instrumentForms(
    CompileContext context,
    ModuleChunk chunk,
    final Map<File, String> chunkSourcePath,
    final ClassLoader loader,
    Collection<File> formsToInstrument,
    OutputFilesSink outputSink) throws ProjectBuildException {

    final Map<String, File> class2form = new HashMap<String, File>();
    final SourceToFormMapping sourceToFormMap = context.getDataManager().getSourceToFormMap();

    final Map<String, OutputFileObject> compiledClassNames = new HashMap<String, OutputFileObject>();
    for (OutputFileObject fileObject : outputSink.getFileObjects()) {
      compiledClassNames.put(fileObject.getClassName(), fileObject);
    }

    final MyNestedFormLoader nestedFormsLoader = new MyNestedFormLoader(
      chunkSourcePath, ProjectPaths.getOutputPathsWithDependents(chunk, context.isCompilingTests())
    );

    for (File formFile : formsToInstrument) {
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(formFile.toURI().toURL(), new CompiledClassPropertiesProvider(loader));
      }
      catch (AlienFormFileException e) {
        // ignore non-IDEA forms
        continue;
      }
      catch (Exception e) {
        throw new ProjectBuildException("Cannot process form file " + formFile.getAbsolutePath(), e);
      }

      final String classToBind = rootContainer.getClassToBind();
      if (classToBind == null) {
        continue;
      }

      final OutputFileObject outputClassFile = findClassFile(compiledClassNames, classToBind);
      if (outputClassFile == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, "Class to bind does not exist: " + classToBind, formFile.getAbsolutePath()));
        continue;
      }

      final File alreadyProcessedForm = class2form.get(classToBind);
      if (alreadyProcessedForm != null) {
        context.processMessage(new CompilerMessage(
          BUILDER_NAME, BuildMessage.Kind.WARNING,
          formFile.getAbsolutePath() + ": The form is bound to the class " + classToBind + ".\nAnother form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class",
          formFile.getAbsolutePath())
        );
        continue;
      }

      class2form.put(classToBind, formFile);

      boolean success = true;
      try {
        final OutputFileObject.Content originalContent = outputClassFile.getContent();
        final ClassReader classReader = new ClassReader(originalContent.getBuffer(), originalContent.getOffset(), originalContent.getLength());

        final int version = getClassFileVersion(classReader);
        final InstrumenterClassWriter classWriter = new InstrumenterClassWriter(getAsmClassWriterFlags(version), loader);
        final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader, nestedFormsLoader, false, classWriter);
        final byte[] patchedBytes = codeGenerator.patchClass(classReader);
        if (patchedBytes != null) {
          outputClassFile.updateContent(patchedBytes);
        }

        final FormErrorInfo[] warnings = codeGenerator.getWarnings();
        for (final FormErrorInfo warning : warnings) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, warning.getErrorMessage(), formFile.getAbsolutePath()));
        }

        final FormErrorInfo[] errors = codeGenerator.getErrors();
        if (errors.length > 0) {
          success = false;
          StringBuilder message = new StringBuilder();
          for (final FormErrorInfo error : errors) {
            if (message.length() > 0) {
              message.append("\n");
            }
            message.append(formFile.getAbsolutePath()).append(": ").append(error.getErrorMessage());
          }
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message.toString()));
        }
        else {
          final File sourceFile = outputClassFile.getSourceFile();
          if (sourceFile != null) {
            sourceToFormMap.update(sourceFile.getPath(), formFile.getPath());
          }
        }
      }
      catch (Exception e) {
        success = false;
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Forms instrumentation failed" + e.getMessage(), formFile.getAbsolutePath()));
      }
      finally {
        if (!success) {
          outputSink.markError(outputClassFile);
        }
      }
    }
  }

  private static OutputFileObject findClassFile(Map<String, OutputFileObject> outputs, String classToBind) {
    while (true) {
      final OutputFileObject fo = outputs.get(classToBind);
      if (fo != null) {
        return fo;
      }
      final int dotIndex = classToBind.lastIndexOf('.');
      if (dotIndex <= 0) {
        return null;
      }
      classToBind = classToBind.substring(0, dotIndex) + "$" + classToBind.substring(dotIndex + 1);
    }
  }

  private static int getClassFileVersion(ClassReader reader) {
    final Ref<Integer> result = new Ref<Integer>(0);
    reader.accept(new EmptyVisitor() {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        result.set(version);
      }
    }, 0);
    return result.get();
  }

  private static int getAsmClassWriterFlags(int version) {
    return version >= Opcodes.V1_6 && version != Opcodes.V1_1 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS;
  }

  private static class DiagnosticSink implements DiagnosticOutputConsumer {
    private final CompileContext myContext;
    private volatile int myErrorCount = 0;
    private volatile int myWarningCount = 0;

    public DiagnosticSink(CompileContext context) {
      myContext = context;
    }

    public void outputLineAvailable(String line) {
      if (line != null) {
        //System.err.println(line);
        if (line.startsWith("[") && line.endsWith("]")) {
          final String message = line.substring(1, line.length() - 1);
          if (message.startsWith("parsing")) {
            myContext.processMessage(new ProgressMessage("Parsing sources..."));
          }
          else {
            if (!message.startsWith("total")) {
              myContext.processMessage(new ProgressMessage(FileUtil.toSystemDependentName(message)));
            }
          }
        }
        else if (line.contains("java.lang.OutOfMemoryError")) {
          myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "OutOfMemoryError: insufficient memory"));
        }
      }
    }

    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      final CompilerMessage.Kind kind;
      switch (diagnostic.getKind()) {
        case ERROR:
          kind = BuildMessage.Kind.ERROR;
          myErrorCount++;
          break;
        case MANDATORY_WARNING:
        case WARNING:
        case NOTE:
          kind = BuildMessage.Kind.WARNING;
          myWarningCount++;
          break;
        default:
          kind = BuildMessage.Kind.INFO;
      }
      final JavaFileObject source = diagnostic.getSource();
      final File sourceFile = source != null? Paths.convertToFile(source.toUri()) : null;
      final String srcPath = sourceFile != null? FileUtil.toSystemIndependentName(sourceFile.getPath()) : null;
      myContext.processMessage(new CompilerMessage(
        BUILDER_NAME, kind, diagnostic.getMessage(Locale.US), srcPath,
        diagnostic.getStartPosition(), diagnostic.getEndPosition(), diagnostic.getPosition(),
        diagnostic.getLineNumber(), diagnostic.getColumnNumber()
      ));
    }

    public int getErrorCount() {
      return myErrorCount;
    }

    public int getWarningCount() {
      return myWarningCount;
    }
  }

  private static class OutputFilesSink implements OutputFileConsumer {
    private final CompileContext myContext;
    private final Set<File> mySuccessfullyCompiled = new HashSet<File>();
    private final Set<File> myProblematic = new HashSet<File>();
    private final List<OutputFileObject> myFileObjects = new ArrayList<OutputFileObject>();
    private final Map<String, OutputFileObject> myCompiledClasses = new HashMap<String, OutputFileObject>();

    public OutputFilesSink(CompileContext context) {
      myContext = context;
    }

    public void save(final @NotNull OutputFileObject fileObject) {
      final String className = fileObject.getClassName();
      if (className != null) {
        final OutputFileObject.Content content = fileObject.getContent();
        if (content != null) {
          synchronized (myCompiledClasses) {
            myCompiledClasses.put(className, fileObject);
          }
        }
      }

      synchronized (myFileObjects) {
        myFileObjects.add(fileObject);
      }
    }

    @Nullable
    public OutputFileObject.Content lookupClassBytes(String className) {
      synchronized (myCompiledClasses) {
        final OutputFileObject object = myCompiledClasses.get(className);
        return object != null? object.getContent() : null;
      }
    }

    public List<OutputFileObject> getFileObjects() {
      return Collections.unmodifiableList(myFileObjects);
    }

    public void writePendingData() {
      try {
        if (!myFileObjects.isEmpty()) {
          final FileGeneratedEvent event = new FileGeneratedEvent();
          try {
            for (OutputFileObject fileObject : myFileObjects) {
              try {
                writeToDisk(fileObject);
                final File rootFile = fileObject.getOutputRoot();
                if (rootFile != null) {
                  event.add(rootFile.getPath(), fileObject.getRelativePath());
                }
              }
              catch (IOException e) {
                myContext.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
              }
            }
          }
          finally {
            myContext.processMessage(event);
          }
        }
      }
      finally {
        myFileObjects.clear();
        myCompiledClasses.clear();
      }
    }

    public Set<File> getSuccessfullyCompiled() {
      return Collections.unmodifiableSet(mySuccessfullyCompiled);
    }

    private void writeToDisk(@NotNull OutputFileObject fileObject) throws IOException {
      final OutputFileObject.Content content = fileObject.getContent();
      if (content != null) {
        FileUtil.writeToFile(fileObject.getFile(), content.getBuffer(), content.getOffset(), content.getLength());
      }
      else {
        throw new IOException("Missing content for file " + fileObject.getFile());
      }

      final File source = fileObject.getSourceFile();
      if (source != null && !myProblematic.contains(source)) {
        mySuccessfullyCompiled.add(source);
        final String className = fileObject.getClassName();
        if (className != null) {
          myContext.processMessage(new ProgressMessage("Compiled " + className));
        }
      }

    }

    public void markError(OutputFileObject outputClassFile) {
      final File source = outputClassFile.getSourceFile();
      if (source != null) {
        myProblematic.add(source);
      }
    }
  }

  public static class InstrumenterClassWriter extends ClassWriter {
    private final ClassLoader myClassLoader;

    public InstrumenterClassWriter(int flags, final ClassLoader pseudoLoader) {
      super(flags);
      myClassLoader = pseudoLoader;
    }

    protected String getCommonSuperClass(final String type1, final String type2) {
      Class c, d;
      try {
        //c = Class.forName(type1.replace('/', '.'), true, myClassLoader);
        //d = Class.forName(type2.replace('/', '.'), true, myClassLoader);
        c = myClassLoader.loadClass(type1.replace('/', '.'));
        d = myClassLoader.loadClass(type2.replace('/', '.'));
      }
      catch (Exception e) {
        throw new RuntimeException(e.toString(), e);
      }
      if (c.isAssignableFrom(d)) {
        return type1;
      }
      if (d.isAssignableFrom(c)) {
        return type2;
      }
      if (c.isInterface() || d.isInterface()) {
        return "java/lang/Object";
      }
      else {
        do {
          c = c.getSuperclass();
        }
        while (!c.isAssignableFrom(d));

        return c.getName().replace('.', '/');
      }
    }
  }

  private static class MyNestedFormLoader implements NestedFormLoader {
    private final Map<File, String> mySourceRoots;
    private final Collection<File> myOutputRoots;
    private final HashMap<String, LwRootContainer> myCache = new HashMap<String, LwRootContainer>();

    /**
     * @param sourceRoots all source roots for current module chunk and all dependent recursively
     * @param outputRoots output roots for this module chunk and all dependent recursively
     */
    public MyNestedFormLoader(Map<File, String> sourceRoots, Collection<File> outputRoots) {
      mySourceRoots = sourceRoots;
      myOutputRoots = outputRoots;
    }

    public LwRootContainer loadForm(String formFileName) throws Exception {
      if (myCache.containsKey(formFileName)) {
        return myCache.get(formFileName);
      }

      final String relPath = FileUtil.toSystemIndependentName(formFileName);

      for (Map.Entry<File, String> entry : mySourceRoots.entrySet()) {
        final File sourceRoot = entry.getKey();
        final String prefix = entry.getValue();
        String path = relPath;
        if (prefix != null && FileUtil.startsWith(path, prefix)) {
          path = path.substring(prefix.length());
        }
        final File formFile = new File(sourceRoot, path);
        if (formFile.exists()) {
          final BufferedInputStream stream = new BufferedInputStream(new FileInputStream(formFile));
          try {
            return loadForm(formFileName, stream);
          }
          finally {
            stream.close();
          }
        }
      }

      throw new Exception("Cannot find nested form file " + formFileName);
    }

    private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
      final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
      myCache.put(formFileName, container);
      return container;
    }

    public String getClassToBindName(LwRootContainer container) {
      final String className = container.getClassToBind();
      for (File outputRoot : myOutputRoots) {
        final String result = getJVMClassName(outputRoot, className.replace('.', '/'));
        if (result != null) {
          return result.replace('/', '.');
        }
      }
      return className;
    }
  }

  @Nullable
  private static String getJVMClassName(File outputRoot, String className) {
    while (true) {
      final File candidateClass = new File(outputRoot, className + ".class");
      if (candidateClass.exists()) {
        return className;
      }
      final int position = className.lastIndexOf('/');
      if (position < 0) {
        return null;
      }
      className = className.substring(0, position) + '$' + className.substring(position + 1);
    }
  }

  private static File getResourcePath(Class aClass) {
    return new File(PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class"));
  }

  private static class CompiledClassesLoader extends URLClassLoader {
    private final OutputFilesSink mySink;

    public CompiledClassesLoader(OutputFilesSink sink, URL[] urls) {
      super(urls, null);
      mySink = sink;
    }

    protected Class findClass(String name) throws ClassNotFoundException {
      final OutputFileObject.Content content = mySink.lookupClassBytes(name);
      if (content != null) {
        return defineClass(name, content.getBuffer(), content.getOffset(), content.getLength());
      }
      return super.findClass(name);
    }

    public URL findResource(String name) {
      return super.findResource(name);
    }
  }

  private class ClassProcessingConsumer implements OutputFileConsumer {
    private final CompileContext myCompileContext;
    private final OutputFileConsumer myDelegateOutputFileSink;
    private int myTasksInProgress = 0;
    private final Object myCounterLock = new Object();

    public ClassProcessingConsumer(CompileContext compileContext, OutputFileConsumer sink) {
      myCompileContext = compileContext;
      myDelegateOutputFileSink = sink != null? sink : new OutputFileConsumer() {
        public void save(@NotNull OutputFileObject fileObject) {
          throw new RuntimeException("Output sink for compiler was not specified");
        }
      };
    }

    public void save(@NotNull final OutputFileObject fileObject) {
      incTaskCount();
      myTaskRunner.submit(new Runnable() {
        public void run() {
          try {
            for (ClassPostProcessor processor : myClassProcessors) {
              processor.process(myCompileContext, fileObject);
            }
          }
          finally {
            try {
              myDelegateOutputFileSink.save(fileObject);
            }
            finally {
              decTaskCount();
            }
          }
        }
      });
    }

    private void decTaskCount() {
      synchronized (myCounterLock) {
        myTasksInProgress = Math.max(0, myTasksInProgress - 1);
        if (myTasksInProgress == 0) {
          myCounterLock.notifyAll();
        }
      }
    }

    private void incTaskCount() {
      synchronized (myCounterLock) {
        myTasksInProgress++;
      }
    }

    public void ensurePendingTasksCompleted() {
      synchronized (myCounterLock) {
        while (myTasksInProgress > 0) {
          try {
            myCounterLock.wait();
          }
          catch (InterruptedException ignored) {
          }
        }
      }
    }

  }
}
