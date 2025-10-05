// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.forms.FormBinding;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumenterClassWriter;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.CompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputExplorer;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import com.intellij.tools.build.bazel.uiDesigner.compiler.*;
import com.intellij.tools.build.bazel.uiDesigner.compiler.Utils;
import com.intellij.tools.build.bazel.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.tools.build.bazel.uiDesigner.lw.LwRootContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.util.SystemInfo;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jetbrains.jps.util.Iterators.*;

public class FormsCompiler implements CompilerRunner {
  private static final String CLASS_FILE_EXTENSION = ".class";
  private final BuildContext myContext;
  private final StorageManager myStorageManager;

  public FormsCompiler(BuildContext context, StorageManager storageManager) {
    myContext = context;
    myStorageManager = storageManager;
  }

  @Override
  public String getName() {
    return "Forms Instrumenter";
  }

  @Override
  public boolean canCompile(NodeSource src) {
    return FormBinding.isForm(src);
  }

  @Override
  public ExitCode compile(Iterable<NodeSource> modifiedForms, Iterable<NodeSource> deletedSources, DiagnosticSink diagnostic, OutputSink out) throws Exception {
    // General logic:
    // 1. On the initial round: for all modified forms, using the graph, find all associated source files and ensure they will be recompiled (mark for recompilation)
    // 2. In the output sink, search among just compiled classes those that have associated forms
    // 3. Augment found forms with the explicitly modified forms
    // 4. Use the resulting forms collection to apply instrumentation on just compiled classes

    FormBinding binding = myStorageManager.getFormsBinding();
    if (isEmpty(binding.getAllForms())) {
      return ExitCode.OK;
    }

    NestedFormLoader nestedLoader = null;
    Map<NodeSource, String> toInstrument = new HashMap<>();
    // check all just compiled classes
    for (String jvmClassName : unique(filter(map(out.getNodes(), ns -> ns.node() instanceof JVMClassNode jvmNode? jvmNode.getName() : null), Objects::nonNull))) {
      NodeSource form = binding.getBoundForm(jvmClassName.replace('/', '.').replace('$', '.'));
      if (form != null) {
        toInstrument.put(form, jvmClassName + CLASS_FILE_EXTENSION);
      }
    }

    for (NodeSource form : modifiedForms) {
      if (!toInstrument.containsKey(form)) {
        String boundClass = binding.getBoundClass(form);
        String jvmClassName = getJvmClassEntryPath(out, boundClass);
        if (jvmClassName != null) {
          toInstrument.put(form, jvmClassName);
        }
        else {
          myContext.report(Message.create(this, Message.Kind.ERROR, "Class to bind does not exist in the output: \"" + boundClass + "\"", form.toString()));
        }
      }
    }

    ZipOutputBuilder abiOut = myStorageManager.getAbiOutputBuilder();
    if (abiOut != null) {
      // put form content to abi-out so that it can be later found in the classpath when searching for nested forms
      NodeSourcePathMapper pathMapper = myContext.getPathMapper();
      for (NodeSource form : modifiedForms) {
        Path formPath = pathMapper.toPath(form);
        try (InputStream in = Files.newInputStream(formPath)) {
          abiOut.putEntry(getFormOutputPath(formPath.getFileName().toString()), in.readAllBytes());
        }
      }
    }

    for (Map.Entry<NodeSource, String> entry : toInstrument.entrySet()) {
      if (nestedLoader == null) {
        nestedLoader = new MyNestedFormLoader(binding.getAllForms(), myContext, myStorageManager.getInstrumentationClassFinder(), out);
      }
      instrumentForm(entry.getKey(), entry.getValue(), nestedLoader);
    }
    return ExitCode.OK; 
  }

  private void instrumentForm(NodeSource form, String jvmClassEntryPath, NestedFormLoader nestedLoader) throws MalformedURLException {
    Path formPath = myContext.getPathMapper().toPath(form);
    InstrumentationClassFinder finder = myStorageManager.getInstrumentationClassFinder();
    try {
      final LwRootContainer rootContainer = Utils.getRootContainer(formPath, new CompiledClassPropertiesProvider(finder.getLoader()));
      byte[] content = myStorageManager.getOutputBuilder().getContent(jvmClassEntryPath);
      final ClassReader classReader = new FailSafeClassReader(content);
      final int flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(classReader));
      final InstrumenterClassWriter classWriter = new InstrumenterClassWriter(classReader, flags, finder);
      boolean useDynamicBundles = true; //todo: should be configurable?
      final AsmCodeGenerator codeGenerator = new AsmCodeGenerator(rootContainer, finder, nestedLoader, false, useDynamicBundles , classWriter);
      final byte[] instrumented = codeGenerator.patchClass(classReader);
      if (instrumented != null) {
        myStorageManager.getOutputBuilder().putEntry(jvmClassEntryPath, instrumented);
      }
      for (final FormErrorInfo warning : codeGenerator.getWarnings()) {
        myContext.report(Message.create(this, Message.Kind.WARNING, warning.getErrorMessage(), formPath.toString()));
      }
      for (final FormErrorInfo error : codeGenerator.getErrors()) {
        myContext.report(Message.create(this, Message.Kind.ERROR, error.getErrorMessage(), formPath.toString()));
      }
    }
    catch (AlienFormFileException ignored) {
    }
    catch (UnexpectedFormElementException | UIDesignerException e) {
      myContext.report(Message.create(this, Message.Kind.ERROR, "Error applying form instrumentation: " + e.getMessage(), formPath.toString()));
    }
    catch (Throwable e) {
      myContext.report(Message.create(this, Message.Kind.ERROR, "Error applying instrumentation for the form " + formPath, e));
    }
  }

  private static String getFormOutputPath(String formFileName) {
    // such naming may lead to a conflict between equally named form files,
    // so this implementation makes sure there are no name clashes within the same build target
    return "META-INF/forms/" + formFileName;
  }

  // example
  //     <nested-form id="22f17" form-file="com/jetbrains/php/composer/ComposerExecutionForm.form" binding="myExecutionForm" custom-create="true" default-binding="true">
  private static class MyNestedFormLoader implements NestedFormLoader {
    private final Iterable<NodeSource> myAllForms;
    private final BuildContext myContext;
    private final @NotNull InstrumentationClassFinder myClassFinder;
    private final OutputSink myOutSink;
    private final Map<String, LwRootContainer> myCache = new HashMap<>();

    MyNestedFormLoader(Iterable<NodeSource> allForms, BuildContext context, @NotNull InstrumentationClassFinder classFinder, OutputSink outSink) {
      myAllForms = allForms;
      myContext = context;
      myClassFinder = classFinder;
      myOutSink = outSink;
    }

    @Override
    public LwRootContainer loadForm(String formRelPath) throws Exception {
      LwRootContainer cached = myCache.get(formRelPath);
      if (cached != null) {
        return cached;
      }

      List<Path> found = findNestedForms(formRelPath);
      if (found.size() == 1) {
        Path nested = found.iterator().next();
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(nested))) {
          return loadLwRootContainer(formRelPath, stream);
        }
      }

      if (found.isEmpty()) {
        int idx = formRelPath.lastIndexOf('/');
        String formName = idx >= 0? formRelPath.substring(idx + 1) : formRelPath;
        try (InputStream fromLibraries = myClassFinder.getResourceAsStream(getFormOutputPath(formName))) {
          return loadLwRootContainer(formRelPath, fromLibraries);
        }
        catch (IOException ignored) {
        }

        throw new Exception("Cannot find nested form file " + formRelPath);
      }
      else {
        throw new Exception("Multiple nested forms match \"" + formRelPath + "\": " + found);
      }
    }

    public @NotNull LwRootContainer loadLwRootContainer(String formRelPath, InputStream stream) throws Exception {
      final LwRootContainer container = Utils.getRootContainer(stream, null);
      myCache.put(formRelPath, container);
      return container;
    }

    private List<Path> findNestedForms(String relPath) {
      List<Path> found = new ArrayList<>(1);
      for (String matchPath : nestedPathCandidates(relPath)) {
        collectPaths(matchPath, found);
        if (!found.isEmpty()) {
          break;
        }
      }
      return found;
    }

    private void collectPaths(String relPath, List<Path> acc) {
      for (NodeSource src : myAllForms) {
        String candidate = src.toString();
        int startOffset = candidate.length() - relPath.length();
        if (
          startOffset >=0 &&
          candidate.regionMatches(!SystemInfo.isFileSystemCaseSensitive, startOffset, relPath, 0, relPath.length()) &&
          (startOffset == 0 || candidate.charAt(startOffset - 1) == '/')
        ) {
          Path path = myContext.getPathMapper().toPath(src);
          if (Files.exists(path)) {
            acc.add(path);
          }
        }
      }
    }

    @Override
    public String getClassToBindName(LwRootContainer container) {
      String className = container.getClassToBind();
      for (String candidate : jvmClassNameCandidates(className)) {
        if (myOutSink.getFileContent(candidate + ".class") != null) {
          return candidate.replace('/', '.');
        }
      }
      return className;
    }
  }

  public static @NotNull Iterable<NodeSource> findBoundSources(StorageManager sm, Iterable<NodeSource> forms) throws Exception {
    DependencyGraph graph = sm.getGraph();
    FormBinding binding = sm.getFormsBinding();
    return flat(map(forms, f -> getBoundSources(graph, binding.getBoundClass(f))));
  }

  /**
   * @param graph the dependency graph containing dependency data for already compiled classes
   * @param className a bound class name as specified in the form file
   * @return source files from which the specified bound class was compiled, as per recorded dependency data
   */
  private static @NotNull Iterable<NodeSource> getBoundSources(DependencyGraph graph, String className) {
    if (className != null) {
      for (String candidate : jvmClassNameCandidates(className)) {
        Iterable<NodeSource> sources = graph.getSources(new JvmNodeReferenceID(candidate));
        if (!isEmpty(sources)) {
          return sources;
        }
      }
    }
    return List.of();
  }

  /**
   * @param out the facade for all output classes
   * @param className a bound class name as specified in the form file
   * @return a jvm classname of the specified bound class that actually exists in the output
   */
  private static @Nullable String getJvmClassEntryPath(OutputExplorer out, String className) {
    if (className != null) {
      for (String candidate : jvmClassNameCandidates(className + CLASS_FILE_EXTENSION)) {
        if (out.getFileContent(candidate) != null) {
          return candidate;
        }
      }
    }
    return null;
  }

  private static Iterable<String> jvmClassNameCandidates(String className) {
    return recurse(className.replace('.', '/'), clsName -> {
      final int pos = clsName.lastIndexOf('/');
      return pos < 0 ? List.of() : List.of(clsName.substring(0, pos) + '$' + clsName.substring(pos + 1));
    }, true);
  }

  private static Iterable<String> nestedPathCandidates(String relPath) {
    // Relative paths to nested forms are stored using the source root's package prefix (if it is available).
    // Because there are no directories corresponding to the package prefix on disk,
    // this part of the relative path should be ignored when searching for nested form files
    return recurse(relPath, p -> {
      final int pos = p.indexOf('/');
      return pos < 0 ? List.of() : List.of(p.substring(pos + 1));
    }, true);
  }

}
