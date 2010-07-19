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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 16, 2002
 * Time: 7:14:37 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface ProjectRootContainer {
  @NotNull
  VirtualFile[] getRootFiles(@NotNull OrderRootType type);
  @NotNull ProjectRoot[] getRoots(@NotNull OrderRootType type);

  void startChange();
  void finishChange();

  @NotNull 
  ProjectRoot addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type);
  void addRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type);
  void removeRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type);
  void removeAllRoots(@NotNull OrderRootType type);

  void removeAllRoots();

  void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type);

  void update();
}
