package org.jetbrains.jps.incremental.java;

import com.intellij.ant.PseudoClassLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FileProcessor;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.TimestampStorage;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends Builder{
  public static final String BUILDER_NAME = "java";
  public static final String JAVA_EXTENSION = ".java";
  public static final String FORM_EXTENSION = ".form";
  private static final Key<PseudoClassLoader> PSEUDO_CLASSLOADER_KEY = Key.create("_preudo_class_loader");

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

  private static final String JAVAC_COMPILER_NAME = "javac";
  private final EmbeddedJavac myJavacCompiler;

  public JavaBuilder(ExecutorService tasksExecutor) {
    myJavacCompiler = new EmbeddedJavac(tasksExecutor);
    //add here class processors in the sequence they should be executed
    myJavacCompiler.addClassProcessor(new EmbeddedJavac.ClassPostProcessor() {
      public void process(CompileContext context, OutputFileObject out) {
        final PseudoClassLoader loader = PSEUDO_CLASSLOADER_KEY.get(context);
        if (loader != null) {
          final String className = out.getClassName();
          if (className != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (loader) {
              loader.defineClass(className.replace('.', '/'), out.getContent().toByteArray());
            }
          }
        }
      }
    });
  }

  public String getDescription() {
    return "Java Builder";
  }

  public ExitCode build(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);
      final Set<File> filesToCompile = new HashSet<File>();
      final List<File> formsToCompile = new ArrayList<File>();
      final Set<String> srcRoots = new HashSet<String>();

      context.processFiles(chunk, new FileProcessor() {
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          if (JAVA_SOURCES_FILTER.accept(file)) {
            srcRoots.add(sourceRoot);
            if (isFileDirty(file, context, tsStorage)) {
              filesToCompile.add(file);
            }
          }
          else if (FORM_SOURCES_FILTER.accept(file)){
            if (isFileDirty(file, context, tsStorage)) {
              formsToCompile.add(file);
            }
          }
          return true;
        }
      });

      for (File form : formsToCompile) {
        for (String root : srcRoots) {
          final File boundSource = getBoundSource(root, form);
          if (boundSource != null) {
            // force compilation of classes that modified forms are bound to
            filesToCompile.add(boundSource);
            break;
          }
        }
      }

      return compile(context, chunk, filesToCompile, formsToCompile);
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (message == null) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        message = "Internal error: \n" + out.toString();
      }
      throw new ProjectBuildException(message, e);
    }
  }

  private File getBoundSource(String srcRoot, File formFile) {
    final String boundClassName = getBoundClassName(formFile);
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
    if (files.isEmpty() && forms.isEmpty()) {
      return ExitCode.OK;
    }

    ProjectPaths paths = ProjectPaths.KEY.get(context);
    if (paths == null) {
      ProjectPaths.KEY.set(context, paths = new ProjectPaths(context.getProject()));
    }

    final Collection<File> classpath = paths.getCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake());
    final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake());
    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk);

    final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);

    // setup loader for instrumentation
    final List<URL> urls = new ArrayList<URL>();
    for (Collection<File> cp : Arrays.asList(platformCp, classpath)) {
      for (File file : cp) {
        urls.add(file.toURI().toURL());
      }
    }
    final PseudoClassLoader pseudoLoader = new PseudoClassLoader(urls.toArray(new URL[urls.size()]));
    PSEUDO_CLASSLOADER_KEY.set(context, pseudoLoader);

    final DiagnosticSink diagnosticSink = new DiagnosticSink(context);
    final OutputFilesSink outputSink = new OutputFilesSink(context);

    try {
      final boolean compilationOk = myJavacCompiler.compile(options, files, classpath, platformCp, outs, context, diagnosticSink, outputSink);
      if (!compilationOk || diagnosticSink.getErrorCount() > 0) {
        throw new ProjectBuildException("Compilation failed: errors: " + diagnosticSink.getErrorCount() + "; warnings: " + diagnosticSink.getWarningCount());
      }

      instrumentForms(context, pseudoLoader, forms, outs);

      // todo: add notNull

      return ExitCode.OK;
    }
    finally {
      outputSink.writePendingData();

      PSEUDO_CLASSLOADER_KEY.set(context, null);
      for (File file : outputSink.getSuccessfullyCompiled()) {
        tsStorage.saveStamp(file);
      }
    }
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk) {
    return Arrays.asList("-verbose")/*Collections.emptyList()*/;
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new HashMap<File, Set<File>>();
    final boolean compilingTests = context.isCompilingTests();
    for (Module module : chunk.getModules()) {
      final String outputPath;
      final Collection<String> srcPaths;
      if (compilingTests) {
        outputPath = module.getTestOutputPath();
        srcPaths = (Collection<String>)module.getTestRoots();
      }
      else {
        outputPath = module.getOutputPath();
        srcPaths = (Collection<String>)module.getSourceRoots();
      }
      final Set<File> roots = new HashSet<File>();
      for (String path : srcPaths) {
        roots.add(new File(path));
      }
      map.put(new File(outputPath), roots);
    }
    return map;
  }


  private String getBoundClassName(File formFile) {
    return null; // todo
  }

  private void instrumentForms(CompileContext context, final PseudoClassLoader loader, Collection<File> formsToInstrument, Map<File, Set<File>> outs) throws ProjectBuildException {
    final Map<String, File> class2form = new HashMap<String, File>();

    for (File formFile : formsToInstrument) {
      final File outputDir = findOutputDir(formFile, outs);
      if (outputDir == null) {
        context.processMessage(new CompilerMessage(JavaBuilder.JAVAC_COMPILER_NAME, BuildMessage.Kind.ERROR, "No output directory found for the form", formFile.getAbsolutePath()));
        continue;
      }
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(formFile.toURI().toURL(), new CompiledClassPropertiesProvider(loader.getLoader()));
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

      // todo: temporarily commented
      //final File classFile = getClassFile(outputDir, classToBind.replace('.', '/'));
      //if (classFile == null) {
      //  context.processMessage(new CompilerMessage(JavaBuilder.JAVAC_COMPILER_NAME, BuildMessage.Kind.WARNING, "Class to bind does not exist: " + classToBind, formFile.getAbsolutePath()));
      //  continue;
      //}
      //
      //final File alreadyProcessedForm = class2form.get(classToBind);
      //if (alreadyProcessedForm != null) {
      //  context.processMessage(new CompilerMessage(
      //    JavaBuilder.JAVAC_COMPILER_NAME, BuildMessage.Kind.WARNING,
      //    formFile.getAbsolutePath() + ": The form is bound to the class " + classToBind + ".\nAnother form " + alreadyProcessedForm.getAbsolutePath() + " is also bound to this class",
      //    formFile.getAbsolutePath())
      //  );
      //  continue;
      //}
      //
      //class2form.put(classToBind, formFile);
      //
      //try {
      //  int version;
      //  InputStream stream = new FileInputStream(classFile);
      //  try {
      //    version = getClassFileVersion(new ClassReader(stream));
      //  }
      //  finally {
      //    stream.close();
      //  }
      //  AntClassWriter classWriter = new AntClassWriter(getAsmClassWriterFlags(version), loader);
      //  AntNestedFormLoader formLoader = new AntNestedFormLoader(loader.getLoader(), myNestedFormPathList);
      //  final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, loader.getLoader(), formLoader, false, classWriter);
      //  codeGenerator.patchFile(classFile);
      //
      //  final FormErrorInfo[] warnings = codeGenerator.getWarnings();
      //  for (final FormErrorInfo warning : warnings) {
      //    context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.WARNING, warning.getErrorMessage(), formFile.getAbsolutePath()));
      //  }
      //
      //  final FormErrorInfo[] errors = codeGenerator.getErrors();
      //  if (errors.length > 0) {
      //    StringBuilder message = new StringBuilder();
      //    for (final FormErrorInfo error : errors) {
      //      if (message.length() > 0) {
      //        message.append("\n");
      //      }
      //      message.append(formFile.getAbsolutePath()).append(": ").append(error.getErrorMessage());
      //    }
      //    context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.ERROR, message.toString()));
      //  }
      //}
      //catch (Exception e) {
      //  context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.ERROR, "Forms instrumentation failed" + e.getMessage(), formFile.getAbsolutePath()));
      //}
    }
  }

  private static File findOutputDir(File src, Map<File, Set<File>> outs) {
    if (outs.isEmpty()) {
      return null;
    }
    if (outs.size() == 1) {
      return outs.keySet().iterator().next();
    }

    File file = src.getParentFile();
    while (file != null) {
      for (Map.Entry<File, Set<File>> entry : outs.entrySet()) {
        if (entry.getValue().contains(file)) {
          return entry.getKey();
        }
      }
      file = file.getParentFile();
    }
    return null;
  }


  //private File getClassFile(File outputDir, String className) {
  //  final String classOrInnerName = getClassOrInnerName(outputDir, className);
  //  return classOrInnerName != null? new File(outputDir, classOrInnerName + ".class") : null;
  //}

  //private String getClassOrInnerName(final File outputDir, String className) {
  //  final File classFile = new File(outputDir, className + ".class");
  //  if (classFile.exists()) {
  //    return className;
  //  }
  //  int position = className.lastIndexOf('/');
  //  if (position == -1) {
  //    return null;
  //  }
  //  return getClassOrInnerName(outputDir, className.substring(0, position) + '$' + className.substring(position + 1));
  //}

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

  private static class DiagnosticSink implements EmbeddedJavac.DiagnosticOutputConsumer {

    private final CompileContext myContext;
    private volatile int myErrorCount = 0;
    private volatile int myWarningCount = 0;

    public DiagnosticSink(CompileContext context) {
      myContext = context;
    }

    public void outputLineAvailable(String line) {
      myContext.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.INFO, line));
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
          kind = BuildMessage.Kind.WARNING;
          myWarningCount++;
          break;
        default:
          kind = BuildMessage.Kind.INFO;
      }
      final String srcPath;
      final JavaFileObject source = diagnostic.getSource();
      if (source != null) {
        srcPath = FileUtil.toSystemIndependentName(new File(source.toUri()).getPath());
      }
      else {
        srcPath = null;
      }
      myContext.processMessage(new CompilerMessage(
        JAVAC_COMPILER_NAME, kind, diagnostic.getMessage(Locale.US), srcPath,
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

  private static class OutputFilesSink implements EmbeddedJavac.OutputFileConsumer {

    private final CompileContext myContext;
    private final Set<File> mySuccessfullyCompiled = new HashSet<File>();
    private final List<OutputFileObject> myUnsavedFiles = new ArrayList<OutputFileObject>();

    public OutputFilesSink(CompileContext context) {
      myContext = context;
    }

    public void save(OutputFileObject fileObject) {
      try {
        if (shouldInstrument(fileObject)) {
          myUnsavedFiles.add(fileObject);
        }
        else {
          writeToDisk(fileObject);
        }
      }
      catch (IOException e) {
        myContext.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
      }
    }

    public List<OutputFileObject> getUnsavedFiles() {
      return myUnsavedFiles;
    }

    public void writePendingData() {
      for (OutputFileObject file : myUnsavedFiles) {
        try {
          writeToDisk(file);
        }
        catch (IOException e) {
          myContext.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
        }
      }
    }

    public Set<File> getSuccessfullyCompiled() {
      return mySuccessfullyCompiled;
    }

    private boolean shouldInstrument(OutputFileObject fileObject) {
      return false; // todo!!!
    }

    private void writeToDisk(OutputFileObject fileObject) throws IOException {
      final OutputFileObject.Content content = fileObject.getContent();
      if (content != null) {
        FileUtil.writeToFile(fileObject.getFile(), content.getBuffer(), content.getOffset(), content.getLength());
      }
      else {
        throw new IOException("Missing content for file " + fileObject.getFile());
      }

      myContext.processMessage(new ProgressMessage("Compiled " + fileObject.getFile().getPath()));

      final JavaFileObject source = fileObject.getSource();
      if (source != null) {
        final File file = new File(source.toUri());
        synchronized (mySuccessfullyCompiled) {
          mySuccessfullyCompiled.add(file);
        }
      }
    }
  }

  // todo: temporarily commented
  //private class AntNestedFormLoader implements NestedFormLoader {
  //  private final ClassLoader myLoader;
  //  private final List myNestedFormPathList;
  //  private final HashMap myFormCache = new HashMap();
  //
  //  public AntNestedFormLoader(final ClassLoader loader, List nestedFormPathList) {
  //    myLoader = loader;
  //    myNestedFormPathList = nestedFormPathList;
  //  }
  //
  //  public LwRootContainer loadForm(String formFilePath) throws Exception {
  //    if (myFormCache.containsKey(formFilePath)) {
  //      return (LwRootContainer)myFormCache.get(formFilePath);
  //    }
  //
  //    String lowerFormFilePath = formFilePath.toLowerCase();
  //    for (Iterator iterator = myFormFiles.iterator(); iterator.hasNext();) {
  //      File file = (File)iterator.next();
  //      String name = file.getAbsolutePath().replace(File.separatorChar, '/').toLowerCase();
  //      if (name.endsWith(lowerFormFilePath)) {
  //        return loadForm(formFilePath, new FileInputStream(file));
  //      }
  //    }
  //
  //    if (myNestedFormPathList != null) {
  //      for (int i = 0; i < myNestedFormPathList.size(); i++) {
  //        PrefixedPath path = (PrefixedPath)myNestedFormPathList.get(i);
  //        File formFile = path.findFile(formFilePath);
  //        if (formFile != null) {
  //          return loadForm(formFilePath, new FileInputStream(formFile));
  //        }
  //      }
  //    }
  //    InputStream resourceStream = myLoader.getResourceAsStream(formFilePath);
  //    if (resourceStream != null) {
  //      return loadForm(formFilePath, resourceStream);
  //    }
  //    throw new Exception("Cannot find nested form file " + formFilePath);
  //  }
  //
  //  private LwRootContainer loadForm(String formFileName, InputStream resourceStream) throws Exception {
  //    final LwRootContainer container = Utils.getRootContainer(resourceStream, null);
  //    myFormCache.put(formFileName, container);
  //    return container;
  //  }
  //
  //  public String getClassToBindName(LwRootContainer container) {
  //    final String className = container.getClassToBind();
  //    String result = getClassOrInnerName(className.replace('.', '/'));
  //    if (result != null) return result.replace('/', '.');
  //    return className;
  //  }
  //}

}
