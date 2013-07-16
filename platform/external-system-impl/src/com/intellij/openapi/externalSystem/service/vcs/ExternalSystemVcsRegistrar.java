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
package com.intellij.openapi.externalSystem.service.vcs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import git4idea.roots.GitRootDetector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;

/**
 * It's rather often when open-source project is backed by an external system and stored at public repo hosting
 * like github, bitbucket etc. Standard actions sequence looks as below then:
 * <pre>
 * <ol>
 *   <li>Clone target repo to local machine;</li>
 *   <li>Execute 'import from external system' wizard;</li>
 * </ol>
 * </pre>
 * The problem is that the ide detects unregistered vcs roots at the newly imported project and shows corresponding notification
 * with suggestion to configure vcs roots for the current project. We want to simplify that in a way to not showing the notification
 * but register them automatically. This class manages that.
 * 
 * @author Denis Zhdanov
 * @since 7/15/13 6:00 PM
 */
public class ExternalSystemVcsRegistrar {

  @SuppressWarnings("unchecked")
  public static void handle(@NotNull final Project project) {

    // VCS api doesn't offer generic ability to detect and configure VCS roots at the moment(see IDEA-102703 for more details).
    // That's why we have only git support here.

    final Ref<GitPlatformFacade> gitFacade = new Ref<GitPlatformFacade>();
    try {
      gitFacade.set(ServiceManager.getService(GitPlatformFacade.class));
    }
    catch (Throwable e) {
      // Assuming that git integration is disabled
    }

    if (gitFacade.get() == null) {
      // Assuming that git integration is disabled
      return;
    }
    
    for (final ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      final AbstractExternalSystemSettings settings = manager.getSettingsProvider().fun(project);
      settings.subscribe(new ExternalSystemSettingsListenerAdapter() {
        @Override
        public void onProjectsLinked(@NotNull final Collection linked) {
          final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
          ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
          for (Object o : linked) {
            final ExternalProjectSettings settings = (ExternalProjectSettings)o;
            
            // Hack into git processing.
            Project projectProxy = (Project)Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class[]{Project.class},
              new InvocationHandler() {
                @SuppressWarnings("ConstantConditions")
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                  if ("getBaseDir".equals(method.getName())) {
                    return fileSystem.refreshAndFindFileByPath(settings.getExternalProjectPath());
                  }
                  return method.invoke(project, args);
                }
              }
            );

            Collection<VirtualFile> roots = new GitRootDetector(projectProxy, gitFacade.get()).detect().getRoots();
            if (!roots.isEmpty()) {
              List<VcsDirectoryMapping> mappings = ContainerUtilRt.newArrayList(vcsManager.getDirectoryMappings());
              
              // There is a possible case that no VCS mappings are configured for the current project. There is a single
              // mapping like <Project> - <No VCS> then. We want to replace it if only one mapping to the project root dir
              // has been detected then.
              if (roots.size() == 1
                  && mappings.size() == 1
                  && StringUtil.isEmpty(mappings.get(0).getVcs())
                  && roots.iterator().next().equals(project.getBaseDir()))
              {
                mappings.clear();
                mappings.add(new VcsDirectoryMapping("", GitVcs.getKey().getName()));
              }
              else {
                for (VirtualFile root : roots) {
                  mappings.add(new VcsDirectoryMapping(root.getPath(), GitVcs.getKey().getName()));
                }
              }
              
              vcsManager.setDirectoryMappings(mappings);
            }
          }
        }
      });
    }
  }
}
