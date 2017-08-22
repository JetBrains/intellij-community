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
package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manager of the reference graph for a global inspection run.
 *
 * @author anna
 * @see com.intellij.codeInspection.GlobalInspectionContext#getRefManager()
 * @since 6.0
 */
public abstract class RefManager {
  /**
   * Runs the specified visitor through all elements in the reference graph.
   *
   * @param visitor the visitor to run.
   */
  public abstract void iterate(@NotNull RefVisitor visitor);

  /**
   * Returns the analysis scope for which the reference graph has been built.
   *
   * @return the analysis scope.
   */
  @Nullable
  public abstract AnalysisScope getScope();

  /**
   * Returns the project for which the reference graph has been built.
   *
   * @return the project instance.
   */
  @NotNull
  public abstract Project getProject();

  /**
   * Returns the reference graph node pointing to the project for which the reference
   * graph has been built.
   *
   * @return the node for the project.
   */
  @NotNull
  public abstract RefProject getRefProject();

  /**
   * Creates (if necessary) and returns the reference graph node for the specified module.
   *
   * @param module the module for which the reference graph node is requested.
   * @return the node for the module, or null if {@code module} is null.
   */
  @Nullable
  public abstract RefModule getRefModule(Module module);

  /**
   * Creates (if necessary) and returns the reference graph node for the specified PSI element.
   *
   * @param elem the element for which the reference graph node is requested.
   * @return the node for the element, or null if the element is not valid or does not have
   * a corresponding reference graph node type (is not a field, method, class or file).
   */
  @Nullable
  public abstract RefElement getReference(PsiElement elem);

  /**
   * Creates (if necessary) and returns the reference graph node for the PSI element specified by its type and FQName.
   *
   * @param type   {@link SmartRefElementPointer#FILE, etc.}
   * @param fqName fully qualified name for the element
   * @return the node for the element, or null if the element is not found or does not have
   *         a corresponding reference graph node type.
   */
  @Nullable
  public abstract RefEntity getReference(String type, String fqName);

  public abstract long getLastUsedMask();

  public abstract <T> T getExtension(@NotNull Key<T> key);

  @Nullable
  public abstract String getType(final RefEntity ref);

  @NotNull
  public abstract RefEntity getRefinedElement(@NotNull RefEntity ref);

  public abstract Element export(@NotNull RefEntity entity, @NotNull Element element, final int actualLine);

  @Nullable
  public abstract String getGroupName(final RefElement entity);

  public abstract boolean belongsToScope(PsiElement psiElement);

  public abstract String getQualifiedName(RefEntity refEntity);

  public abstract void removeRefElement(@NotNull RefElement refElement, @NotNull List<RefElement> deletedRefs);

  @NotNull
  public abstract PsiManager getPsiManager();

  /**
   * @return false if no {@link com.intellij.codeInspection.lang.RefManagerExtension} was registered for language and is not covered by default implementation for PsiClassOwner
   * true, otherwise
   */
  public boolean isInGraph(VirtualFile file) {
    return true;
  }
}
