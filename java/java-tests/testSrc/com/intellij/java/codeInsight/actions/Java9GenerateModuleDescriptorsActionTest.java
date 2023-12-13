// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.actions;

import com.intellij.JavaTestUtil;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.conversion.ModuleSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.JobKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer.*;

public class Java9GenerateModuleDescriptorsActionTest extends LightMultiFileTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new FakeModuleDescriptor(Paths.get(getTestDataPath() + "/" + getTestName(true)));
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/actions/generateModuleDescriptors";
  }

  public void testSingleModule() throws IOException {
    performReformatAction();
  }

  public void testSingleModuleWithDependency() throws IOException {
    performReformatAction();
  }

  public void testDependentModules() throws IOException {
    performReformatAction();
  }

  protected void performReformatAction() throws IOException {
    // INIT
    final AnAction action = ActionManager.getInstance().getAction("GenerateModuleDescriptors");
    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return Collections.emptyList();
      if (CommonDataKeys.PROJECT.is(dataId)) return getProject();
      return null;
    });

    // EXEC
    action.actionPerformed(event);

    // CHECK
    final FakeModuleDescriptor descriptor = (FakeModuleDescriptor)getProjectDescriptor();

    PlatformTestUtil.assertDirectoriesEqual(LocalFileSystem.getInstance().findFileByNioFile(descriptor.myAfterPath),
                                            LocalFileSystem.getInstance().findFileByPath(getProject().getBasePath()));
  }

  private static class FakeModuleDescriptor extends DefaultLightProjectDescriptor {
    private final Path myBeforePath;
    private final Path myAfterPath;
    private final Path myProjectPath;

    FakeModuleDescriptor(@NotNull Path path) {
      myBeforePath = path.resolve("before");
      myAfterPath = path.resolve("after");
      myProjectPath = TemporaryDirectory.generateTemporaryPath(ProjectImpl.LIGHT_PROJECT_NAME);
    }

    @Override
    public @NotNull Path getProjectPath() {
      return myProjectPath;
    }

    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk11(); // TODO
    }

    @Override
    public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
      WriteAction.run(() -> {
        final Path basePath = Paths.get(project.getBasePath());
        FileUtil.copyDir(myBeforePath.toFile(), basePath.toFile());
        VfsUtil.markDirtyAndRefresh(false, true, true, basePath.toFile());

        final Element miscXml = JDomSerializationUtil.findComponent(JDOMUtil.load(basePath.resolve(".idea").resolve("misc.xml")),
                                                                    "ProjectRootManager");
        final String outputUrl = miscXml.getChild("output").getAttributeValue("url").replace("$PROJECT_DIR$", basePath.toString());
        final CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(project);

        compilerProjectExtension.setCompilerOutputUrl(outputUrl);
        final VirtualFilePointer pointer = VirtualFilePointerManager.getInstance()
          .create(outputUrl, (Disposable)compilerProjectExtension, null);
        compilerProjectExtension.setCompilerOutputPointer(pointer);
        //CompilerModuleExtension
        //ModuleElementsEditor
        ServiceContainerUtil.replaceService(project, DumbService.class,
                                            new DumbServiceImpl(project, CoroutineScopeKt.CoroutineScope(JobKt.Job(null))) {
                                              @Override
                                              public void smartInvokeLater(@NotNull Runnable runnable) {
                                                runnable.run();
                                              }
                                            }, project);
        ServiceContainerUtil.replaceService(project, CompilerManager.class, new CompilerManagerImpl(project) {
          @Override
          public boolean isUpToDate(@NotNull CompileScope scope) {
            return true;
          }
        }, project);

        final Element modulesXml = JDomSerializationUtil.findComponent(JDOMUtil.load(basePath.resolve(".idea").resolve("modules.xml")),
                                                                       JpsProjectLoader.MODULE_MANAGER_COMPONENT);
        final Element modulesElement = modulesXml.getChild(JpsProjectLoader.MODULES_TAG);
        final List<Element> moduleElements = modulesElement.getChildren(JpsProjectLoader.MODULE_TAG);
        for (Element moduleAttr : moduleElements) {
          Path modulePath = Paths.get(moduleAttr.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
                                        .replace("$PROJECT_DIR$", basePath.toString()));
          final ModuleDescriptor descriptor = new ModuleDescriptor(modulePath);
          final Module module = makeModule(project, descriptor);
          handler.moduleCreated(module);
          final VirtualFile vSrc = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(descriptor.src());
          handler.sourceRootCreated(vSrc);
          createContentEntry(module, vSrc);
        }
      });
    }

    @NotNull
    private Module makeModule(@NotNull Project project, @NotNull ModuleDescriptor descriptor)
      throws ModuleWithNameAlreadyExists, IOException {
      final Module module;
      Path iml = getIml(descriptor.basePath);
      if (iml != null && Files.exists(iml)) {
        module = ModuleManager.getInstance(project).loadModule(iml);
      }
      else {
        iml = descriptor.basePath.resolve(descriptor.basePath.getFileName() + ".iml");
        module = createModule(project, iml);
      }
      ModuleRootModificationUtil.updateModel(module, model -> configureModule(module, model, descriptor));
      return module;
    }

    private void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ModuleDescriptor descriptor) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(descriptor.languageLevel());
      model.setSdk(IdeaTestUtil.getMockJdk(descriptor.languageLevel().toJavaVersion()));
      final BiConsumer<Path, JpsModuleSourceRootType<?>> register = (path, type) -> {
        if (path == null) return;
        final VirtualFile src = Files.exists(path)
                                ? VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
                                : createSourceRoot(module, path.toString());
        registerSourceRoot(module.getProject(), src);
        model.addContentEntry(src).addSourceFolder(src, type);
      };
      register.accept(descriptor.src(), JavaSourceRootType.SOURCE);
      register.accept(descriptor.testSrc(), JavaSourceRootType.TEST_SOURCE);
      //JavaResourceRootType.RESOURCE
      //JavaResourceRootType.TEST_RESOURCE

      // Maven
      final Path mavenOutputPath = Paths.get(module.getModuleFilePath()).getParent().resolve("target").resolve("classes");
      if (Files.exists(mavenOutputPath)) {
        final CompilerModuleExtension compiler = model.getModuleExtension(CompilerModuleExtension.class);
        compiler.setCompilerOutputPath(mavenOutputPath.toString());
        compiler.inheritCompilerOutputPath(false);
      }
    }

    private record ModuleDescriptor(@NotNull String name, @NotNull Path basePath, @NotNull Path src, @Nullable Path testSrc,
                                    @NotNull LanguageLevel languageLevel) {
      @SuppressWarnings("SwitchStatementWithTooFewBranches")
      private ModuleDescriptor(@NotNull Path iml) throws IOException, JDOMException {
        this(iml.getFileName().toString().replace(".iml", ""), iml.getParent(),
             Paths.get(new URL(getData(iml, List.of(CONTENT_TAG, SOURCE_FOLDER_TAG), e -> switch (e.getName()) {
               case SOURCE_FOLDER_TAG -> e.getAttributeValue(IS_TEST_SOURCE_ATTRIBUTE).equals("false");
               default -> true;
             }).stream().findFirst().orElseThrow().getAttributeValue(URL_ATTRIBUTE)
                                 .replace("$MODULE_DIR$", iml.getParent().toString())).getPath()), null, LanguageLevel.JDK_11);
      }

      private static List<Element> getData(@NotNull Path iml, List<String> tags, @NotNull Predicate<Element> condition)
        throws IOException, JDOMException {
        final Element component = JDomSerializationUtil.findComponent(JDOMUtil.load(iml), ModuleSettings.MODULE_ROOT_MANAGER_COMPONENT);
        List<Element> elements = List.of(component);
        for (String tag : tags) {
          List<Element> newElements = new ArrayList<>();
          for (Element element : elements) {
            newElements.addAll(element.getChildren(tag).stream().filter(condition).toList());
          }
          elements = newElements;
        }
        return elements;
      }
    }
  }

  @Nullable
  private static Path getIml(@NotNull Path path) {
    return findFiles(path, "glob:**/*.iml").stream().findFirst().orElse(null);
  }

  @NotNull
  private static List<Path> findFiles(@NotNull Path path, @NotNull String mask) {
    final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(mask);
    try (final Stream<Path> stream = Files.walk(path)) {
      return stream.filter(matcher::matches).sorted().toList();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
