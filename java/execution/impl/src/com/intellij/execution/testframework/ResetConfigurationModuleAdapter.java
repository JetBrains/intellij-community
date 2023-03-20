// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.HashSet;
import java.util.Set;

public class ResetConfigurationModuleAdapter extends HyperlinkAdapter {
  private static final Logger LOG = Logger.getInstance(ResetConfigurationModuleAdapter.class);
  private final Project myProject;
  private final boolean myIsDebug;
  private final ToolWindowManager myToolWindowManager;
  private final String myTestRunDebugId;
  private final ModuleBasedConfiguration myConfiguration;

  public ResetConfigurationModuleAdapter(ModuleBasedConfiguration configuration, final Project project,
                                         final boolean isDebug,
                                         final ToolWindowManager toolWindowManager,
                                         final String testRunDebugId) {
    myProject = project;
    myIsDebug = isDebug;
    myToolWindowManager = toolWindowManager;
    myTestRunDebugId = testRunDebugId;
    myConfiguration = configuration;
  }

  public static <T extends ModuleBasedConfiguration<JavaRunConfigurationModule, Element> & CommonJavaRunConfigurationParameters>
  boolean tryWithAnotherModule(T configuration, boolean isDebug) {
    final String packageName = configuration.getPackage();
    if (packageName == null) return false;
    final Project project = configuration.getProject();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (aPackage == null) return false;
    final Module module = configuration.getConfigurationModule().getModule();
    if (module == null) return false;
    final String testRunDebugId = isDebug ? ToolWindowId.DEBUG : ToolWindowId.RUN;
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (configuration instanceof JavaTestConfigurationBase &&
        ((JavaTestConfigurationBase)configuration).getTestSearchScope() == TestSearchScope.SINGLE_MODULE) {
      PsiDirectory[] directories = ReadAction.compute(() -> aPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module)));
      if (directories.length > ReadAction.compute(() -> aPackage.getDirectories(GlobalSearchScope.moduleScope(module))).length) {
        String message = new HtmlBuilder().append(JavaBundle.message("popup.content.tests.were.not.found.in.module", module.getName()))
          .appendLink("scope", JavaBundle.message("popup.content.tests.were.not.found.in.module.search.in.dependencies"))
          .toString();
        ResetConfigurationModuleAdapter listener = new ResetConfigurationModuleAdapter(configuration, project, isDebug, toolWindowManager, testRunDebugId) {
            @Override
            protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
              ((JavaTestConfigurationBase)configuration).setSearchScope(TestSearchScope.MODULE_WITH_DEPENDENCIES);
              restart();
            }
          };
        UIUtil.invokeLaterIfNeeded(() -> toolWindowManager.notifyByBalloon(testRunDebugId, MessageType.WARNING, message, null, listener));
        return true;
      }
    }
    final Set<Module> modulesWithPackage = new HashSet<>();
    ReadAction.run(() -> {
      final PsiDirectory[] directories = ReadAction.compute(() -> aPackage.getDirectories());
      for (PsiDirectory directory : directories) {
        final Module currentModule = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project);
        if (module != currentModule && currentModule != null) {
          modulesWithPackage.add(currentModule);
        }
      }
    });
    if (!modulesWithPackage.isEmpty()) {
      
      final Function<Module, String> moduleNameRef = module1 -> {
        final String moduleName = module1.getName();
        return "<a href=\"" + moduleName + "\">" + moduleName + "</a>";
      };
      String message = JavaBundle.message("popup.content.tests.were.not.found.in.module", module.getName()) +
                       JavaBundle.message("popup.content.tests.were.not.found.in.module.use.instead", 
                                          modulesWithPackage.size() == 1 ? 0 : 1,
                                          moduleNameRef.fun(modulesWithPackage.iterator().next()),
                                          StringUtil.join(modulesWithPackage, moduleNameRef, "\n"));
      UIUtil.invokeLaterIfNeeded(() ->
                                   toolWindowManager.notifyByBalloon(testRunDebugId, MessageType.WARNING, message, null,
                                                                     new ResetConfigurationModuleAdapter(configuration, project, isDebug, toolWindowManager, testRunDebugId)));
      return true;
    }
    return false;
  }

  @Override
  protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
    final Module moduleByName = ModuleManager.getInstance(myProject).findModuleByName(e.getDescription());
    if (moduleByName != null) {
      myConfiguration.getConfigurationModule().setModule(moduleByName);
      restart();
    }
  }

  protected void restart() {
    try {
      Executor executor = myIsDebug ? DefaultDebugExecutor.getDebugExecutorInstance()
                                    : DefaultRunExecutor.getRunExecutorInstance();
      ExecutionEnvironmentBuilder.create(myProject, executor, myConfiguration).contentToReuse(null).buildAndExecute();
      Balloon balloon = myToolWindowManager.getToolWindowBalloon(myTestRunDebugId);
      if (balloon != null) {
        balloon.hide();
      }
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
  }
}
