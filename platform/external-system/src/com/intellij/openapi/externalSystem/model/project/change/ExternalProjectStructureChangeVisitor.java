/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project.change;

import org.jetbrains.annotations.NotNull;

/**
 * Defines common interface for dispatching gradle project structure change objects.
 * 
 * @author Denis Zhdanov
 * @since 11/16/11 8:48 PM
 */
public interface ExternalProjectStructureChangeVisitor {
  void visit(@NotNull GradleProjectRenameChange change);
  void visit(@NotNull LanguageLevelChange change);
  void visit(@NotNull ModulePresenceChange change);
  void visit(@NotNull ContentRootPresenceChange change);
  void visit(@NotNull LibraryDependencyPresenceChange change);
  void visit(@NotNull JarPresenceChange change);
  void visit(@NotNull OutdatedLibraryVersionChange change);
  void visit(@NotNull ModuleDependencyPresenceChange change);
  void visit(@NotNull DependencyScopeChange change);
  void visit(@NotNull DependencyExportedChange change);
}
