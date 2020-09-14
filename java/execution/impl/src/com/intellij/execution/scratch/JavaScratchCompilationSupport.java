// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.scratch;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaSdkType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
final class JavaScratchCompilationSupport implements CompileTask {
  @Nullable
  public static File getScratchOutputDirectory(Project project) {
    final File root = CompilerManager.getInstance(project).getJavacCompilerWorkingDir();
    return root != null? new File(root, "scratches/out") : null;
  }

  @Nullable
  public static File getScratchTempDirectory(Project project) {
    final File root = CompilerManager.getInstance(project).getJavacCompilerWorkingDir();
    return root != null? new File(root, "scratches/src") : null;
  }

  @Override
  public boolean execute(@NotNull CompileContext context) {
    final Project project = context.getProject();

    final RunConfiguration configuration = CompileStepBeforeRun.getRunConfiguration(context);
    if (!(configuration instanceof JavaScratchConfiguration)) {
      return true;
    }
    final JavaScratchConfiguration scratchConfig = (JavaScratchConfiguration)configuration;
    final String scratchUrl = scratchConfig.getScratchFileUrl();
    if (scratchUrl == null) {
      context.addMessage(CompilerMessageCategory.ERROR, ExecutionBundle.message("run.java.scratch.associated.file.not.specified"), null, -1, -1);
      return false;
    }
    @Nullable
    final Module module = scratchConfig.getConfigurationModule().getModule();
    final Sdk targetSdk = module != null? ModuleRootManager.getInstance(module).getSdk() : ProjectRootManager.getInstance(project).getProjectSdk();
    if (targetSdk == null) {
      final String message = module != null?
        ExecutionBundle.message("run.java.scratch.missing.jdk.module", module.getName()) :
        ExecutionBundle.message("run.java.scratch.missing.jdk");
      context.addMessage(CompilerMessageCategory.ERROR, message, scratchUrl, -1, -1);
      return true;
    }
    if (!(targetSdk.getSdkType() instanceof JavaSdkType)) {
      final String message = module != null?
        ExecutionBundle.message("run.java.scratch.java.sdk.required.module", module.getName()) :
        ExecutionBundle.message("run.java.scratch.java.sdk.required.project", project.getName());
      context.addMessage(CompilerMessageCategory.ERROR, message, scratchUrl, -1, -1);
      return true;
    }

    final File outputDir = getScratchOutputDirectory(project);
    if (outputDir == null) { // should not happen for normal projects
      return true;
    }
    FileUtil.delete(outputDir); // perform cleanup

    try {
      final File scratchFile = new File(VirtualFileManager.extractPath(scratchUrl));
      File srcFile = scratchFile;

      VirtualFile vFile = ReadAction.compute(() -> VirtualFileManager.getInstance().findFileByUrl(scratchUrl));
      Charset charset = ReadAction.compute(() -> vFile == null ? null : vFile.getCharset());

      if (!StringUtil.endsWith(srcFile.getName(), ".java")) {

        final File srcDir = getScratchTempDirectory(project);
        if (srcDir == null) { // should not happen for normal projects
          return true;
        }
        FileUtil.delete(srcDir); // perform cleanup

        final String srcFileName = ReadAction.compute(() -> {
          if (vFile != null) {
            final PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
            if (psiFile instanceof PsiJavaFile) {
              String name = null;
              // take the name of the first found public top-level class, otherwise the name of any available top-level class
              for (PsiClass aClass : ((PsiJavaFile)psiFile).getClasses()) {
                if (name == null) {
                  name = aClass.getName();
                  if (isPublic(aClass)) {
                    break;
                  }
                }
                else if (isPublic(aClass)) {
                  name = aClass.getName();
                  break;
                }
              }
              if (name != null) {
                return name;
              }
            }
          }
          return FileUtilRt.getNameWithoutExtension(scratchFile.getName());
        });
        srcFile = new File(srcDir, srcFileName + ".java");
        FileUtil.copy(scratchFile, srcFile);
      }

      final Collection<File> files = Collections.singleton(srcFile);

      final Set<File> cp = new LinkedHashSet<>();
      final List<File> platformCp = new ArrayList<>();

      final Computable<OrderEnumerator> orderEnumerator = module != null ? () -> ModuleRootManager.getInstance(module).orderEntries()
                                                                         : () -> ProjectRootManager.getInstance(project).orderEntries();

      ApplicationManager.getApplication().runReadAction(() -> {
        if (module != null || scratchConfig.isBuildProjectOnEmptyModuleList()) {
          for (String s : orderEnumerator.compute().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().getPathList()) {
            cp.add(new File(s));
          }
        }
        for (String s : orderEnumerator.compute().compileOnly().sdkOnly().getPathsList().getPathList()) {
          platformCp.add(new File(s));
        }
      });

      final List<String> options = new ArrayList<>();
      options.add("-g"); // always compile with debug info
      final JavaSdkVersion sdkVersion = JavaSdk.getInstance().getVersion(targetSdk);
      if (sdkVersion != null) {
        final LanguageLevel level = sdkVersion.getMaxLanguageLevel();
        final String langLevel = JpsJavaSdkType.complianceOption(level.toJavaVersion());
        options.add("-source");
        options.add(langLevel);
        options.add("-target");
        options.add(langLevel);
        if (level.isPreview()) {
          options.add("--enable-preview");
        }
      }
      options.add("-proc:none"); // disable annotation processing
      if (charset != null) {
        options.add("-encoding");
        options.add(charset.name());
      }
      final Collection<ClassObject> result = CompilerManager.getInstance(project).compileJavaCode(
        options, platformCp, cp, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), files, outputDir
      );
      for (ClassObject classObject : result) {
        final byte[] bytes = classObject.getContent();
        if (bytes != null) {
          FileUtil.writeToFile(new File(classObject.getPath()), bytes);
        }
      }
    }
    catch (CompilationException e) {
      for (CompilationException.Message m : e.getMessages()) {
        context.addMessage(m.getCategory(), m.getText(), scratchUrl, m.getLine(), m.getColumn());
      }
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), scratchUrl, -1, -1);
    }
    return true;
  }

  private static boolean isPublic(PsiClass aClass) {
    final PsiModifierList modifiers = aClass.getModifierList();
    return modifiers != null && modifiers.hasModifierProperty(PsiModifier.PUBLIC);
  }
}