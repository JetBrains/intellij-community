package com.intellij.ide.structureView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Belyaev
 */
public abstract class StructureViewFactoryEx extends StructureViewFactory {

  @Nullable
  public abstract StructureViewWrapper getStructureViewWrapper();

  public abstract void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension);
  public abstract void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension);

  public abstract Collection<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type);

  public abstract void setActiveAction(final String name, final boolean state);

  public abstract boolean isActionActive(final String name);

  public static StructureViewFactoryEx getInstanceEx(final Project project) {
    return (StructureViewFactoryEx)getInstance(project);
  }

  public abstract void runWhenInitialized(Runnable runnable);
}