/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.ModuleParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JavaModuleInsight extends ModuleInsight {
  private static final Logger LOG = Logger.getInstance(JavaModuleInsight.class);
  private final Lexer myLexer;

  public JavaModuleInsight(@Nullable final ProgressIndicator progress,
                           Set<String> existingModuleNames,
                           Set<String> existingProjectLibraryNames) {
    super(progress, existingModuleNames, existingProjectLibraryNames);
    myLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);
  }

  @Override
  public void scanModules() {
    scanModuleInfoFiles();

    super.scanModules();
  }

  private void scanModuleInfoFiles() {
    final List<DetectedSourceRoot> allRoots = super.getSourceRootsToScan();
    final List<JavaModuleSourceRoot> moduleInfoRoots = StreamEx
      .of(allRoots)
      .select(JavaModuleSourceRoot.class)
      .filter(JavaModuleSourceRoot::isWithModuleInfoFile)
      .filter(root -> !isIgnoredName(root.getDirectory()))
      .toList();
    if (moduleInfoRoots.isEmpty()) {
      return;
    }
    myProgress.setIndeterminate(true);
    myProgress.pushState();
    try {
      Map<String, ModuleInfo> moduleInfos = new HashMap<>();
      for (JavaModuleSourceRoot moduleInfoRoot : moduleInfoRoots) {
        final File sourceRoot = moduleInfoRoot.getDirectory();
        myProgress.setText("Scanning " + sourceRoot.getPath());
        final ModuleInfo moduleInfo = scanModuleInfoFile(sourceRoot);
        if (moduleInfo != null) {
          moduleInfo.descriptor = createModuleDescriptor(moduleInfo.directory, Collections.singletonList(moduleInfoRoot));
          moduleInfos.put(moduleInfo.name, moduleInfo);
          addExportedPackages(sourceRoot, moduleInfo.exportsPackages);
        }
      }
      myProgress.setText("Building modules layout...");
      for (ModuleInfo moduleInfo : moduleInfos.values()) {
        for (String requiresModule : moduleInfo.requiresModules) {
          ModuleInfo requiredModuleInfo = moduleInfos.get(requiresModule);
          if (requiredModuleInfo != null) {
            moduleInfo.descriptor.addDependencyOn(requiredModuleInfo.descriptor);
          }
        }
      }

      addModules(StreamEx.of(moduleInfos.values()).map(info -> info.descriptor).toList());
    }
    catch (ProcessCanceledException ignored) {
    }
    finally {
      myProgress.popState();
    }
  }

  @NotNull
  @Override
  protected List<DetectedSourceRoot> getSourceRootsToScan() {
    final List<DetectedSourceRoot> allRoots = super.getSourceRootsToScan();
    return ContainerUtil.filter(allRoots, r -> !(r instanceof JavaModuleSourceRoot) || !((JavaModuleSourceRoot)r).isWithModuleInfoFile());
  }

  private ModuleInfo scanModuleInfoFile(@NotNull File directory) {
    File file = new File(directory, "module-info.java");
    myProgress.setText2(file.getName());
    try {
      final char[] chars = FileUtil.loadFileText(file);
      String text = new String(chars);

      ModuleInfo moduleInfo = ReadAction.compute(() -> {
        Project project = ProjectManager.getInstance().getDefaultProject();
        PsiManager manager = PsiManager.getInstance(project);
        JavaDummyElement dummyElement = new JavaDummyElement(text, builder -> ModuleParser.parseModule(builder), LanguageLevel.JDK_1_9);
        DummyHolder holder = DummyHolderFactory.createHolder(manager, dummyElement, null);
        PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement());
        PsiJavaModule javaModule = PsiTreeUtil.getChildOfType(element, PsiJavaModule.class);
        if (javaModule == null) {
          throw new IncorrectOperationException("Incorrect module declaration '" + file.getPath() + "'");
        }
        ModuleInfo info = new ModuleInfo(javaModule.getModuleName());
        javaModule.accept(new ModuleInfoVisitor(info));
        return info;
      });

      File moduleDirectory = directory;
      while (!isEntryPointRoot(moduleDirectory) && !moduleInfo.name.equals(moduleDirectory.getName())) {
        File parent = moduleDirectory.getParentFile();
        if (parent == null) break;
        moduleDirectory = parent;
      }
      moduleInfo.directory = moduleDirectory;

      return moduleInfo;
    }
    catch (IOException | IncorrectOperationException e) {
      LOG.info(e);
      return null;
    }
  }

  @Override
  protected boolean isSourceFile(final File file) {
    return StringUtil.endsWithIgnoreCase(file.getName(), ".java");
  }

  @Override
  protected boolean isLibraryFile(final String fileName) {
    return StringUtil.endsWithIgnoreCase(fileName, ".jar") || StringUtil.endsWithIgnoreCase(fileName, ".zip");
  }

  @Override
  protected void scanSourceFileForImportedPackages(final CharSequence chars, final Consumer<String> result) {
    myLexer.start(chars);

    JavaSourceRootDetectionUtil.skipWhiteSpaceAndComments(myLexer);
    if (myLexer.getTokenType() == JavaTokenType.PACKAGE_KEYWORD) {
      advanceLexer(myLexer);
      if (readPackageName(chars, myLexer) == null) {
        return;
      }
    }

    while (true) {
      if (myLexer.getTokenType() == JavaTokenType.SEMICOLON) {
        advanceLexer(myLexer);
      }
      if (myLexer.getTokenType() != JavaTokenType.IMPORT_KEYWORD) {
        return;
      }
      advanceLexer(myLexer);

      boolean isStaticImport = false;
      if (myLexer.getTokenType() == JavaTokenType.STATIC_KEYWORD) {
        isStaticImport = true;
        advanceLexer(myLexer);
      }

      final String packageName = readPackageName(chars, myLexer);
      if (packageName == null) {
        return;
      }

      if (packageName.endsWith(".*")) {
        result.consume(packageName.substring(0, packageName.length() - ".*".length()));
      }
      else {
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
          String _packageName = packageName.substring(0, lastDot);
          if (isStaticImport) {
            lastDot = _packageName.lastIndexOf('.');
            if (lastDot > 0) {
              result.consume(_packageName.substring(0, lastDot));
            }
          }
          else {
            result.consume(_packageName);
          }
        }
      }
    }
  }

  @Nullable
  private static String readPackageName(final CharSequence text, final Lexer lexer) {
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while (true) {
        if (lexer.getTokenType() != JavaTokenType.IDENTIFIER && lexer.getTokenType() != JavaTokenType.ASTERISK) {
          break;
        }
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());

        advanceLexer(lexer);
        if (lexer.getTokenType() != JavaTokenType.DOT) {
          break;
        }
        buffer.append('.');

        advanceLexer(lexer);
      }

      String packageName = buffer.toString();
      if (packageName.length() == 0 || StringUtil.endsWithChar(packageName, '.') || StringUtil.startsWithChar(packageName, '*')) {
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
    JavaSourceRootDetectionUtil.skipWhiteSpaceAndComments(lexer);
  }

  @Override
  protected void scanLibraryForDeclaredPackages(File file, Consumer<String> result) throws IOException {
    final ZipFile zip = new ZipFile(file);
    try {
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        final String entryName = entries.nextElement().getName();
        if (StringUtil.endsWithIgnoreCase(entryName, ".class")) {
          final int index = entryName.lastIndexOf('/');
          if (index > 0) {
            final String packageName = entryName.substring(0, index).replace('/', '.');
            result.consume(packageName);
          }
        }
      }
    }
    finally {
      zip.close();
    }
  }

  protected ModuleDescriptor createModuleDescriptor(final File moduleContentRoot, final Collection<DetectedSourceRoot> sourceRoots) {
    return new ModuleDescriptor(moduleContentRoot, StdModuleTypes.JAVA, sourceRoots);
  }

  public boolean isApplicableRoot(final DetectedProjectRoot root) {
    return root instanceof JavaModuleSourceRoot;
  }

  private static class ModuleInfo {
    @NotNull final String name;
    final Set<String> requiresModules = new HashSet<>();
    final Set<String> exportsPackages = new HashSet<>();

    File directory;
    ModuleDescriptor descriptor;

    private ModuleInfo(@NotNull String name) {this.name = name;}
  }

  private static class ModuleInfoVisitor extends JavaRecursiveElementVisitor {
    private final ModuleInfo myInfo;

    public ModuleInfoVisitor(ModuleInfo info) {myInfo = info;}

    @Override
    public void visitRequiresStatement(PsiRequiresStatement statement) {
      super.visitRequiresStatement(statement);
      PsiJavaModuleReferenceElement referenceElement = statement.getReferenceElement();
      if (referenceElement != null) {
        String referenceText = referenceElement.getReferenceText();
        myInfo.requiresModules.add(referenceText);
      }
    }

    @Override
    public void visitExportsStatement(PsiExportsStatement statement) {
      super.visitExportsStatement(statement);
      PsiJavaCodeReferenceElement reference = statement.getPackageReference();
      if (reference != null) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName != null) {
          myInfo.exportsPackages.add(qualifiedName);
        }
      }
    }
  }
}
