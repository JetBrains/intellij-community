// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JavaModuleInsight extends ModuleInsight {
  private static final Logger LOG = Logger.getInstance(JavaModuleInsight.class);
  private final Lexer myLexer;

  public JavaModuleInsight(final @Nullable ProgressIndicator progress,
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
        myProgress.setText(JavaUiBundle.message("module.insight.scan.progress.text.scanning", sourceRoot.getPath()));
        final ModuleInfo moduleInfo = scanModuleInfoFile(sourceRoot);
        if (moduleInfo != null) {
          moduleInfo.descriptor = createModuleDescriptor(moduleInfo.directory, Collections.singletonList(moduleInfoRoot));
          moduleInfos.put(moduleInfo.name, moduleInfo);
          addExportedPackages(sourceRoot, moduleInfo.exportsPackages);
        }
      }
      myProgress.setText(JavaUiBundle.message("module.insight.scan.progress.text.building.modules.layout"));
      for (ModuleInfo moduleInfo : moduleInfos.values()) {
        for (String requiresModule : moduleInfo.requiresModules) {
          ModuleInfo requiredModuleInfo = moduleInfos.get(requiresModule);
          if (requiredModuleInfo != null) {
            moduleInfo.descriptor.addDependencyOn(requiredModuleInfo.descriptor);
          }
        }
      }

      addModules(ContainerUtil.map(moduleInfos.values(), info -> info.descriptor));
    }
    catch (ProcessCanceledException ignored) { }
    finally {
      myProgress.popState();
    }
  }

  @Override
  protected @NotNull @Unmodifiable List<DetectedSourceRoot> getSourceRootsToScan() {
    final List<DetectedSourceRoot> allRoots = super.getSourceRootsToScan();
    return ContainerUtil.filter(allRoots, r -> !(r instanceof JavaModuleSourceRoot) || !((JavaModuleSourceRoot)r).isWithModuleInfoFile());
  }

  private ModuleInfo scanModuleInfoFile(@NotNull File directory) {
    File file = new File(directory, PsiJavaModule.MODULE_INFO_FILE);
    final @NlsSafe String name = file.getName();
    myProgress.setText2(name);
    try {
      String text = FileUtil.loadFile(file);

      PsiFileFactory factory = PsiFileFactory.getInstance(ProjectManager.getInstance().getDefaultProject());
      ModuleInfo moduleInfo = ReadAction.compute(() -> {
        PsiFile psiFile = factory.createFileFromText(PsiJavaModule.MODULE_INFO_FILE, JavaFileType.INSTANCE, text);
        PsiJavaModule javaModule = psiFile instanceof PsiJavaFile ? ((PsiJavaFile)psiFile).getModuleDeclaration() : null;
        if (javaModule == null) {
          throw new IncorrectOperationException("Incorrect module declaration '" + file.getPath() + "'");
        }
        ModuleInfo info = new ModuleInfo(javaModule.getName());
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
  protected void scanSourceFileForImportedPackages(final CharSequence chars, final Consumer<? super String> result) {
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

  private static @Nullable String readPackageName(final CharSequence text, final Lexer lexer) {
    final StringBuilder buffer = new StringBuilder();
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
    if (packageName.isEmpty() || StringUtil.endsWithChar(packageName, '.') || StringUtil.startsWithChar(packageName, '*')) {
      return null;
    }
    return packageName;
  }

  private static void advanceLexer(final Lexer lexer) {
    lexer.advance();
    JavaSourceRootDetectionUtil.skipWhiteSpaceAndComments(lexer);
  }

  @Override
  protected void scanLibraryForDeclaredPackages(File file, Consumer<? super String> result) throws IOException {
    try (ZipFile zip = new ZipFile(file)) {
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
  }

  @Override
  protected ModuleDescriptor createModuleDescriptor(final File moduleContentRoot, final Collection<DetectedSourceRoot> sourceRoots) {
    return new ModuleDescriptor(moduleContentRoot, JavaModuleType.getModuleType(), sourceRoots);
  }

  @Override
  public boolean isApplicableRoot(final DetectedProjectRoot root) {
    return root instanceof JavaModuleSourceRoot;
  }

  private static final class ModuleInfo {
    final String name;
    final Set<String> requiresModules = new HashSet<>();
    final Set<String> exportsPackages = new HashSet<>();

    File directory;
    ModuleDescriptor descriptor;

    private ModuleInfo(@NotNull String name) {
      this.name = name;
    }
  }

  private static class ModuleInfoVisitor extends JavaRecursiveElementVisitor {
    private final ModuleInfo myInfo;

    ModuleInfoVisitor(ModuleInfo info) {
      myInfo = info;
    }

    @Override
    public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
      super.visitRequiresStatement(statement);
      String referenceText = statement.getModuleName();
      if (referenceText != null) {
        myInfo.requiresModules.add(referenceText);
      }
    }

    @Override
    public void visitPackageAccessibilityStatement(@NotNull PsiPackageAccessibilityStatement statement) {
      super.visitPackageAccessibilityStatement(statement);
      if (statement.getRole() == PsiPackageAccessibilityStatement.Role.EXPORTS) {
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
}