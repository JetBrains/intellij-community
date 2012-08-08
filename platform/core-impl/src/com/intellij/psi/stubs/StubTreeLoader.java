/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class StubTreeLoader {

  public static StubTreeLoader getInstance() {
    return ServiceManager.getService(StubTreeLoader.class);
  }

  @Nullable
  public abstract ObjectStubTree readOrBuild(Project project, final VirtualFile vFile, @Nullable final PsiFile psiFile);

  @Nullable
  public abstract ObjectStubTree readFromVFile(Project project, final VirtualFile vFile);
  
  public abstract void rebuildStubTree(VirtualFile virtualFile);

  public abstract long getStubTreeTimestamp(VirtualFile vFile);

  public abstract boolean canHaveStub(VirtualFile file);
}
