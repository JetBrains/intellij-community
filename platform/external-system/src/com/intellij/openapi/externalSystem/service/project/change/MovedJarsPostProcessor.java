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
package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.JarData;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangeVisitor;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangeVisitorAdapter;
import com.intellij.openapi.externalSystem.model.project.change.JarPresenceChange;
import com.intellij.openapi.externalSystem.model.project.id.JarId;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.service.project.manage.JarDataService;
import com.intellij.openapi.externalSystem.util.ArtifactInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;

import java.util.*;

/**
 * {@link ExternalProjectStructureChangesPostProcessor} implementation which adjusts library jars paths when it detects that they
 * have been moved.
 * <p/>
 * Target use-case: external system uses version-specific file system directory for holding downloaded library dependencies.
 * I.e. when we build project info with external system of version X, it puts all jars into directory Dx. But when
 * external system version is changed to Y, it will store all jars into directory Dy (different from Dx). So, following
 * scenario is possible:
 * <pre>
 * <ol>
 *   <li>A user imports IJ project from the external system v.X;</li>
 *   <li>All necessary jars are downloaded and stored at Dx;</li>
 *   <li>A user switched the external system to version Y;</li>
 *   <li>
 *     When the project is refreshed we have a number of changes like 'external-system-local jar with location at Dy' and
 *     'ide-local jar with location at Dx';
 *   </li>
 * </ol>
 * </pre>
 * We want to avoid that by auto-adjusting ide library path config within the moved jars info.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 1/16/13 6:19 PM
 */
public class MovedJarsPostProcessor implements ExternalProjectStructureChangesPostProcessor {

  @NotNull private final JarDataService myJarManager;

  public MovedJarsPostProcessor(@NotNull JarDataService manager) {
    myJarManager = manager;
  }

  @Override
  public void processChanges(@NotNull final Collection<ExternalProjectStructureChange> changes,
                             @NotNull ProjectSystemId externalSystemId,
                             @NotNull final Project project,
                             boolean onIdeProjectStructureChange)
  {
    if (onIdeProjectStructureChange) {
      // Do nothing as project models modification implied by project model change event are prohibited (see IDEA-100625).
      return;
    }
    final Collection<MergeInfo> toMerge = buildMergeData(project, changes, ServiceManager.getService(ProjectStructureServices.class));
    if (toMerge == null) {
      return;
    }

    Runnable mergeTask = new Runnable() {
      @Override
      public void run() {
        for (MergeInfo info : toMerge) {
          // TODO den implement
//          myJarManager.removeJars(Collections.singleton(info.ideJar), project, true);
//          myJarManager.importJars(Collections.singleton(info.gradleJar), project, true);
          changes.removeAll(info.changes);
        }
      }
    };
    doMerge(mergeTask, project);
  }

  /**
   * This method is introduced in order to allow to cut 'Execute EDT/Execute under write action' etc stuff during test execution.
   *
   * @param mergeTask  merge changes function object
   * @param project    target project
   */
  public void doMerge(@NotNull final Runnable mergeTask, @NotNull final Project project) {
    ExternalSystemUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, mergeTask, true, new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(mergeTask);
      }
    });
  }

  @Nullable
  static Collection<MergeInfo> buildMergeData(@NotNull Project project,
                                              @NotNull Collection<ExternalProjectStructureChange> changes,
                                              @NotNull ProjectStructureServices services)
  {
    final Map<String, Set<JarPresenceChange>> changesByLibrary = ContainerUtilRt.newHashMap();
    ExternalProjectStructureChangeVisitor visitor = new ExternalProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull JarPresenceChange change) {
        JarId id = change.getIdeEntity();
        if (id == null) {
          id = change.getExternalEntity();
        }
        assert id != null;
        String libraryName = id.getLibraryId().getLibraryName();
        Set<JarPresenceChange> c = changesByLibrary.get(libraryName);
        if (c == null) {
          changesByLibrary.put(libraryName, c = ContainerUtilRt.newHashSet());
        }
        c.add(change);
      }
    };
    for (ExternalProjectStructureChange change : changes) {
      change.invite(visitor);
    }

    final Collection<MergeInfo> toMerge = ContainerUtilRt.newArrayList();
    for (Set<JarPresenceChange> c : changesByLibrary.values()) {
      processLibraryJarChanges(project, c, services, toMerge);
    }
    if (toMerge.isEmpty()) {
      return null;
    }
    return toMerge;
  }

  private static void processLibraryJarChanges(@NotNull Project project,
                                               @NotNull Set<JarPresenceChange> changes,
                                               @NotNull ProjectStructureServices services,
                                               @NotNull Collection<MergeInfo> toMerge)
  {
    Map<LibraryPathType, Map<ArtifactInfo, JarPresenceChange>> gradleLocalJars = ContainerUtilRt.newHashMap();
    Map<LibraryPathType, Map<ArtifactInfo, JarPresenceChange>> ideLocalJars = ContainerUtilRt.newHashMap();
    for (JarPresenceChange change : changes) {
      Map<LibraryPathType, Map<ArtifactInfo, JarPresenceChange>> storageToUse = gradleLocalJars;
      JarId entity = change.getExternalEntity();
      if (entity == null) {
        entity = change.getIdeEntity();
        assert entity != null;
        storageToUse = ideLocalJars;
      }
      ArtifactInfo artifactInfo = ExternalSystemUtil.parseArtifactInfo(entity.getPath());
      if (artifactInfo != null) {
        Map<ArtifactInfo, JarPresenceChange> m = storageToUse.get(entity.getLibraryPathType());
        if (m == null) {
          storageToUse.put(entity.getLibraryPathType(), m = ContainerUtilRt.newHashMap());
        }
        m.put(artifactInfo, change);
      }
    }

    for (Map.Entry<LibraryPathType, Map<ArtifactInfo, JarPresenceChange>> entry : gradleLocalJars.entrySet()) {
      for (ArtifactInfo info : entry.getValue().keySet()) {
        Map<ArtifactInfo, JarPresenceChange> m = ideLocalJars.get(entry.getKey());
        if (m == null) {
          continue;
        }
        JarPresenceChange ideLocalJarChange = m.get(info);
        if (ideLocalJarChange == null) {
          continue;
        }
        JarId ideJarId = ideLocalJarChange.getIdeEntity();
        assert ideJarId != null;
        JarData ideJar = ideJarId.mapToEntity(services, project);
        if (ideJar == null) {
          continue;
        }

        JarPresenceChange gradleLocalJarChange = entry.getValue().get(info);
        JarId jarId = gradleLocalJarChange.getExternalEntity();
        assert jarId != null;
        JarData gradleJar = jarId.mapToEntity(services, project);
        if (gradleJar == null) {
          continue;
        }

        toMerge.add(new MergeInfo(gradleJar, ideJar, gradleLocalJarChange, ideLocalJarChange));
      }
    }
  }

  static class MergeInfo {

    @NotNull public final Collection<JarPresenceChange> changes = ContainerUtilRt.newArrayList();

    @NotNull public final JarData gradleJar;
    @NotNull public final JarData ideJar;

    MergeInfo(@NotNull JarData gradleJar, @NotNull JarData ideJar, JarPresenceChange... changes) {
      this.gradleJar = gradleJar;
      this.ideJar = ideJar;
      this.changes.addAll(Arrays.asList(changes));
    }

    @Override
    public String toString() {
      return String.format("jar '%s' for library '%s'", gradleJar.getName(), gradleJar.getLibraryId().getLibraryName());
    }
  }
}
