/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.compiler.impl.jarr;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactImpl;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class JarBuildParticipantProvider extends BuildParticipantProvider {

  @Override
  public Collection<? extends BuildParticipant> getParticipants(final Module module) {

    if (!Registry.is("jar.build")) return Collections.emptySet();
    
    return Collections.singleton(new BuildParticipant() {
      @Override
      public Artifact createArtifact(CompileContext context) {

        PackagingElementFactory factory = PackagingElementFactory.getInstance();
        ArtifactRootElement<?> root = factory.createArtifactRootElement();

        CompositePackagingElement<?> classesJar = factory.createArchive(module.getName() + ".jar");
        classesJar.addOrFindChild(factory.createModuleOutput(module));
        String s = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests().getPath();
        classesJar.addOrFindChild(factory.createDirectoryCopyWithParentDirectories(s, ""));
        root.addOrFindChild(classesJar);

        Project project = module.getProject();
        VirtualFile output = CompilerProjectExtension.getInstance(project).getCompilerOutput();
        String path = output == null ? null : output.getPath() + "/jars";
        return path == null ? null : new ArtifactImpl(module.getName(), PlainArtifactType.getInstance(), false, root, path);
      }
    });
  }

}
