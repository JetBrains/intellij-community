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
package com.intellij.compiler.make;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.MethodInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.StringBuilderSpinAllocator;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 18, 2003
 * Time: 6:32:32 PM
 */
public class CacheUtils {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CacheUtils");

  public static String[] getParameterSignatures(MethodInfo methodDeclarationId, SymbolTable symbolTable) throws CacheCorruptedException {
    String descriptor = symbolTable.getSymbol(methodDeclarationId.getDescriptor());
    int endIndex = descriptor.indexOf(')');
    if (endIndex <= 0) {
      LOG.error("Corrupted method descriptor: " + descriptor);
    }
    return parseSignature(descriptor.substring(1, endIndex));
  }

  private static String[] parseSignature(String signature) {
    final ArrayList<String> list = new ArrayList<String>();
    String paramSignature = parseParameterSignature(signature);
    while (paramSignature != null && !"".equals(paramSignature)) {
      list.add(paramSignature);
      signature = signature.substring(paramSignature.length());
      paramSignature = parseParameterSignature(signature);
    }
    return ArrayUtil.toStringArray(list);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static @Nullable String parseParameterSignature(String signature) {
    if (StringUtil.startsWithChar(signature, 'B')) {
      return "B";
    }
    if (StringUtil.startsWithChar(signature, 'C')) {
      return "C";
    }
    if (StringUtil.startsWithChar(signature, 'D')) {
      return "D";
    }
    if (StringUtil.startsWithChar(signature, 'F')) {
      return "F";
    }
    if (StringUtil.startsWithChar(signature, 'I')) {
      return "I";
    }
    if (StringUtil.startsWithChar(signature, 'J')) {
      return "J";
    }
    if (StringUtil.startsWithChar(signature, 'S')) {
      return "S";
    }
    if (StringUtil.startsWithChar(signature, 'Z')) {
      return "Z";
    }
    if (StringUtil.startsWithChar(signature, 'L')) {
      return signature.substring(0, signature.indexOf(";") + 1);
    }
    if (StringUtil.startsWithChar(signature, '[')) {
      String s = parseParameterSignature(signature.substring(1));
      return (s != null) ? ("[" + s) : null;
    }
    return null;
  }

  public static String getMethodSignature(String name, String descriptor) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(name);
      builder.append(descriptor.substring(0, descriptor.indexOf(')') + 1));
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public static boolean areArraysContentsEqual(int[] exceptions1, int[] exceptions2) {
    if (exceptions1.length != exceptions2.length) {
      return false;
    }
    if (exceptions1.length != 0) { // optimization
      TIntHashSet exceptionsSet = new TIntHashSet(exceptions1);
      for (int exception : exceptions2) {
        if (!exceptionsSet.contains(exception)) {
          return false;
        }
      }
    }
    return true;
  }

  public static Collection<VirtualFile> findDependentFiles(
    final CompileContextEx context, 
    final Set<VirtualFile> succesfullyCompiledJavaFiles, 
    final @Nullable Function<Pair<int[], Set<VirtualFile>>, Pair<int[], Set<VirtualFile>>> filter) throws CacheCorruptedException {
    
    if (!CompilerConfiguration.MAKE_ENABLED) {
      return Collections.emptyList();
    }
    context.getProgressIndicator().setText(CompilerBundle.message("progress.checking.dependencies"));

    final DependencyCache dependencyCache = context.getDependencyCache();

    final Pair<int[], Set<VirtualFile>> deps =
        dependencyCache.findDependentClasses(context, context.getProject(), succesfullyCompiledJavaFiles);
    final Pair<int[], Set<VirtualFile>> filteredDeps = filter != null? filter.fun(deps) : deps;

    final Set<VirtualFile> dependentFiles = new HashSet<VirtualFile>();
    final CacheCorruptedException[] _ex = {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(context.getProject());
          SourceFileFinder sourceFileFinder = new SourceFileFinder(context.getProject(), context);
          final Cache cache = dependencyCache.getCache();
          for (final int infoQName : filteredDeps.getFirst()) {
            final String qualifiedName = dependencyCache.resolve(infoQName);
            final String sourceFileName = cache.getSourceFileName(infoQName);
            final VirtualFile file = sourceFileFinder.findSourceFile(qualifiedName, sourceFileName);
            if (file != null) {
              if (!compilerConfiguration.isExcludedFromCompilation(file)) {
                dependentFiles.add(file);
                if (ApplicationManager.getApplication().isUnitTestMode()) {
                  LOG.assertTrue(file.isValid());
                  CompilerManagerImpl.addRecompiledPath(file.getPath());
                }
              }
            }
            else {
              LOG.info("No source file for " + dependencyCache.resolve(infoQName) + " found; source file name=" + sourceFileName);
            }
          }
          for (final VirtualFile file : filteredDeps.getSecond()) {
            if (!compilerConfiguration.isExcludedFromCompilation(file)) {
              dependentFiles.add(file);
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                LOG.assertTrue(file.isValid());
                CompilerManagerImpl.addRecompiledPath(file.getPath());
              }
            }
          }
        }
        catch (CacheCorruptedException e) {
          _ex[0] = e;
        }
      }
    });
    if (_ex[0] != null) {
      throw _ex[0];
    }
    context.getProgressIndicator().setText(
      dependentFiles.size() > 0? CompilerBundle.message("progress.found.dependent.files", dependentFiles.size()) : ""
    );

    return dependentFiles;
  }

  @NotNull
  public static Set<VirtualFile> getFilesCompiledWithErrors(final CompileContextEx context) {
    CompilerMessage[] messages = context.getMessages(CompilerMessageCategory.ERROR);
    Set<VirtualFile> compiledWithErrors = Collections.emptySet();
    if (messages.length > 0) {
      compiledWithErrors = new HashSet<VirtualFile>(messages.length);
      for (CompilerMessage message : messages) {
        final VirtualFile file = message.getVirtualFile();
        if (file != null) {
          compiledWithErrors.add(file);
        }
      }
    }
    return compiledWithErrors;
  }
}
