// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manager of the reference graph for a global inspection run.
 *
 * @author anna
 * @see com.intellij.codeInspection.GlobalInspectionContext#getRefManager()
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
   * To submit task during processing of project usages. The task will be run in a read action in parallel on a separate thread if possible.
   * @param runnable  the task to run.
   */
  public void executeTask(Runnable runnable) {
    runnable.run();
  }

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
  public abstract RefModule getRefModule(@Nullable Module module);

  /**
   * Creates (if necessary) and returns the reference graph node for the specified PSI element.
   *
   * @param elem the element for which the reference graph node is requested.
   * @return the node for the element, or null if the element is not valid or does not have
   * a corresponding reference graph node type (is not a field, method, class or file).
   */
  @Nullable
  public abstract RefElement getReference(@Nullable PsiElement elem);

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
  public abstract String getType(@NotNull RefEntity ref);

  @NotNull
  public abstract RefEntity getRefinedElement(@NotNull RefEntity ref);

  @Nullable
  public Element export(@NotNull RefEntity entity, @NotNull Element parent, final int actualLine) {
    Element element = export(entity, actualLine);
    if (element == null) return null;
    parent.addContent(element);
    return element;
  }

  /**
   * Writes specified reference into org.jdom.Element. Serialized data is used for reporting of inspection problems.
   *
   * @param entity     reference to be serialized
   * @param actualLine value is used only if {@code entity } is {@link RefElement}. If {@code actualLine != -1} then
   *                   {@code (actualLine+1)} is used as value for {@code line} element in serialized data.
   * @return element describing specified reference.
   */
  @Nullable
  public Element export(@NotNull RefEntity entity, final int actualLine) {
    throw new UnsupportedOperationException();
  }

  /**
   * Writes specified reference into org.jdom.Element. If {@code entity } is {@link RefElement} then this method
   * in addition to {@link RefManager#export(RefEntity, int)} reports {@code offset}, {@code length} and {@code highlighted_element}
   * elements as children of returned element.
   *
   * @param entity reference to be serialized
   * @return element describing specified reference.
   * @see RefManager#export(RefEntity, int)
   */
  @Nullable
  public Element export(@NotNull RefEntity entity) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public abstract String getGroupName(@NotNull RefElement entity);

  public abstract boolean belongsToScope(@Nullable PsiElement psiElement);

  @Nullable
  public abstract @NlsSafe String getQualifiedName(@Nullable RefEntity refEntity);

  public abstract void removeRefElement(@NotNull RefElement refElement, @NotNull List<? super RefElement> deletedRefs);

  @NotNull
  public abstract PsiManager getPsiManager();

  /**
   * @return false if no {@link com.intellij.codeInspection.lang.RefManagerExtension} was registered for language and is not covered by default implementation for PsiClassOwner
   * true, otherwise
   */
  public boolean isInGraph(VirtualFile file) {
    return true;
  }

  @Nullable
  public PsiNamedElement getContainerElement(@NotNull PsiElement element) {
    return null;
  }
}
