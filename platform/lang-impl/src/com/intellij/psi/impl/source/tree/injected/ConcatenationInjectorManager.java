// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiParameterizedCachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class ConcatenationInjectorManager extends SimpleModificationTracker {
  public static final ExtensionPointName<ConcatenationAwareInjector> CONCATENATION_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.concatenationAwareInjector");

  public ConcatenationInjectorManager(Project project, PsiManagerEx psiManagerEx) {
    final ExtensionPoint<ConcatenationAwareInjector> concatPoint = Extensions.getArea(project).getExtensionPoint(CONCATENATION_INJECTOR_EP_NAME);
    concatPoint.addExtensionPointListener(new ExtensionPointListener<ConcatenationAwareInjector>() {
      @Override
      public void extensionAdded(@NotNull ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerConcatenationInjector(injector);
      }

      @Override
      public void extensionRemoved(@NotNull ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterConcatenationInjector(injector);
      }
    });
    // clear caches even on non-physical changes
    psiManagerEx.registerRunnableToRunOnAnyChange(this::incModificationCount);
  }

  public static ConcatenationInjectorManager getInstance(final Project project) {
    return ServiceManager.getService(project, ConcatenationInjectorManager.class);
  }

  private static InjectionResult doCompute(@NotNull PsiFile containingFile,
                                           @NotNull Project project,
                                           @NotNull PsiElement anchor,
                                           @NotNull PsiElement[] operands) {
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    InjectionRegistrarImpl registrar = new InjectionRegistrarImpl(project, containingFile, anchor, docManager);
    InjectionResult result = null;
    ConcatenationInjectorManager concatenationInjectorManager = getInstance(project);
    for (ConcatenationAwareInjector concatenationInjector : concatenationInjectorManager.myConcatenationInjectors) {
      concatenationInjector.getLanguagesToInject(registrar, operands);
      result = registrar.getInjectedResult();
      if (result != null) break;
    }

    return result;
  }

  private static final Key<ParameterizedCachedValue<InjectionResult, PsiElement>> INJECTED_PSI_IN_CONCATENATION = Key.create("INJECTED_PSI_IN_CONCATENATION");
  private static final Key<Integer> NO_CONCAT_INJECTION_TIMESTAMP = Key.create("NO_CONCAT_INJECTION_TIMESTAMP");

  public abstract static class BaseConcatenation2InjectorAdapter implements MultiHostInjector {
    private final ConcatenationInjectorManager myManager;

    public BaseConcatenation2InjectorAdapter(ConcatenationInjectorManager manager) {
      myManager = manager;
    }

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
      if (myManager.myConcatenationInjectors.isEmpty()) return;

      final PsiFile containingFile = ((InjectionRegistrarImpl)registrar).getHostPsiFile();
      Project project = containingFile.getProject();
      long modificationCount = PsiManager.getInstance(project).getModificationTracker().getModificationCount();
      Pair<PsiElement, PsiElement[]> pair = computeAnchorAndOperands(context);
      PsiElement anchor = pair.first;
      PsiElement[] operands = pair.second;
      Integer noInjectionTimestamp = anchor.getUserData(NO_CONCAT_INJECTION_TIMESTAMP);

      InjectionResult result;
      ParameterizedCachedValue<InjectionResult, PsiElement> data = null;
      if (operands.length == 0 || noInjectionTimestamp != null && noInjectionTimestamp == modificationCount) {
        result = null;
      }
      else {
        data = anchor.getUserData(INJECTED_PSI_IN_CONCATENATION);

        result = data == null ? null : data.getValue(context);
        if (result == null || !result.isValid()) {
          result = doCompute(containingFile, project, anchor, operands);
        }
      }
      if (result != null) {
        ((InjectionRegistrarImpl)registrar).addToResults(result);

        if (data == null) {
          CachedValueProvider.Result<InjectionResult> cachedResult =
            CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT, myManager);
          data = CachedValuesManager.getManager(project).createParameterizedCachedValue(
            context1 -> {
              PsiFile containingFile1 = context1.getContainingFile();
              Project project1 = containingFile1.getProject();
              Pair<PsiElement, PsiElement[]> pair1 = computeAnchorAndOperands(context1);
              InjectionResult result1 = pair1.second.length == 0 ? null : doCompute(containingFile1, project1, pair1.first, pair1.second);
              return result1 == null ? null : CachedValueProvider.Result.create(result1, PsiModificationTracker.MODIFICATION_COUNT, myManager);
            }, false);
          ((PsiParameterizedCachedValue<InjectionResult, PsiElement>)data).setValue(cachedResult);

          anchor.putUserData(INJECTED_PSI_IN_CONCATENATION, data);
          if (anchor.getUserData(NO_CONCAT_INJECTION_TIMESTAMP) != null) {
            anchor.putUserData(NO_CONCAT_INJECTION_TIMESTAMP, null);
          }
        }
      }
      else {
        // cache no-injection flag
        if (anchor.getUserData(INJECTED_PSI_IN_CONCATENATION) != null) {
          anchor.putUserData(INJECTED_PSI_IN_CONCATENATION, null);
        }
        anchor.putUserData(NO_CONCAT_INJECTION_TIMESTAMP, (int)modificationCount);
      }
    }

    protected abstract Pair<PsiElement, PsiElement[]> computeAnchorAndOperands(@NotNull PsiElement context);
  }

  private final List<ConcatenationAwareInjector> myConcatenationInjectors = ContainerUtil.createLockFreeCopyOnWriteList();
  public void registerConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    myConcatenationInjectors.add(injector);
    concatenationInjectorsChanged();
  }

  public boolean unregisterConcatenationInjector(@NotNull ConcatenationAwareInjector injector) {
    boolean removed = myConcatenationInjectors.remove(injector);
    concatenationInjectorsChanged();
    return removed;
  }

  private void concatenationInjectorsChanged() {
    incModificationCount();
  }
}
