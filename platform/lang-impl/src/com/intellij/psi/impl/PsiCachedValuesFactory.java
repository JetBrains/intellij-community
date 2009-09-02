package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.CachedValue;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class PsiCachedValuesFactory implements CachedValuesFactory {

  private final Project myProject;
  private final PsiManager myManager;

  public PsiCachedValuesFactory(PsiManager manager) {
    myManager = manager;
    myProject = manager.getProject();
  }

  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return trackValue ? new PsiCachedValueImpl<T>(myManager, provider) {
      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> result) {
        return getDependenciesPlusValue(result);
      }
    } : new PsiCachedValueImpl<T>(myManager, provider) {
    };
  }

  public <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                              boolean trackValue) {
    return trackValue ? new PsiParameterizedCachedValue<T, P>(myManager, provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }

      @Override
      protected Object[] getDependencies(CachedValueProvider.Result<T> tResult) {
        return getDependenciesPlusValue(tResult);
      }
    } : new PsiParameterizedCachedValue<T, P>(myManager, provider) {
      @Override
      public boolean isFromMyProject(Project project) {
        return myProject == project;
      }
    };
  }
}
