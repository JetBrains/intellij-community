// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.psi.impl.PsiManagerImpl.ANY_PSI_CHANGE_TOPIC;

@Service(Service.Level.PROJECT)
public final class ControlFlowFactory implements Disposable {
  // psiElements hold weakly, controlFlows softly
  private final Map<PsiElement, ConcurrentList<ControlFlowContext>> cachedFlows = CollectionFactory.createConcurrentWeakKeySoftValueMap();

  public static ControlFlowFactory getInstance(Project project) {
    return project.getService(ControlFlowFactory.class);
  }

  public ControlFlowFactory(@NotNull Project project) {
    project.getMessageBus().connect(this).subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) clearCache();
      }
    });
  }

  private void clearCache() {
    cachedFlows.clear();
  }

  void registerSubRange(final PsiElement codeFragment,
                        final ControlFlowSubRange flow,
                        final ControlFlowOptions options,
                        final ControlFlowPolicy policy) {
    registerControlFlow(codeFragment, flow, options, policy);
  }

  /**
   * @param body body element to compute control flow for
   * @return control flow where constants are not evaluated (e.g., unreachable code due to always false loop condition is still present)
   * @throws AnalysisCanceledException if control flow cannot be built (e.g., syntax error inside the body)
   */
  public static @NotNull ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return getControlFlow(body, policy, ControlFlowOptions.NO_CONST_EVALUATE);
  }

  private static final class ControlFlowContext {
    private final ControlFlowPolicy policy;
    private final @NotNull ControlFlowOptions options;
    private final long modificationCount;
    private final @NotNull ControlFlow controlFlow;

    private ControlFlowContext(@NotNull ControlFlowOptions options,
                               @NotNull ControlFlowPolicy policy,
                               long modificationCount,
                               @NotNull ControlFlow controlFlow) {
      this.options = options;
      this.policy = policy;
      this.modificationCount = modificationCount;
      this.controlFlow = controlFlow;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ControlFlowContext that = (ControlFlowContext)o;

      return isFor(that);
    }

    @Override
    public int hashCode() {
      int result = policy.hashCode();
      result = 31 * result + (options.hashCode());
      result = 31 * result + Long.hashCode(modificationCount);
      return result;
    }

    private boolean isFor(@NotNull ControlFlowPolicy policy,
                          @NotNull ControlFlowOptions options,
                          long modificationCount) {
      if (modificationCount != this.modificationCount) return false;
      if (!policy.equals(this.policy)) return false;
      if (!options.equals(this.options)) {
        // optimization: when no constant condition were computed, both control flows are the same
        return !controlFlow.isConstantConditionOccurred() && this.options.dontEvaluateConstantIfCondition().equals(options);
      }
      return true;
    }

    private boolean isFor(@NotNull ControlFlowContext that) {
      return isFor(that.policy, that.options, that.modificationCount);
    }
  }

  public @NotNull ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy) throws AnalysisCanceledException {
    return doGetControlFlow(element, policy, ControlFlowOptions.create(true, true, true));
  }

  public @NotNull ControlFlow getControlFlow(@NotNull PsiElement element, @NotNull ControlFlowPolicy policy, boolean evaluateConstantIfCondition) throws AnalysisCanceledException {
    return doGetControlFlow(element, policy, ControlFlowOptions.create(true, evaluateConstantIfCondition, true));
  }

  public static @NotNull ControlFlow getControlFlow(@NotNull PsiElement element,
                                                    @NotNull ControlFlowPolicy policy,
                                                    @NotNull ControlFlowOptions options) throws AnalysisCanceledException {
    return getInstance(element.getProject()).doGetControlFlow(element, policy, options);
  }

  private @NotNull ControlFlow doGetControlFlow(@NotNull PsiElement element,
                                                @NotNull ControlFlowPolicy policy,
                                                @NotNull ControlFlowOptions options) throws AnalysisCanceledException {
    if (!element.isPhysical()) {
      return new ControlFlowAnalyzer(element, policy, options).buildControlFlow();
    }
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ConcurrentList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    for (ControlFlowContext context : cached) {
      if (context.isFor(policy, options, modificationCount)) return context.controlFlow;
    }
    ControlFlow controlFlow = new ControlFlowAnalyzer(element, policy, options).buildControlFlow();
    ControlFlowContext context = createContext(options, policy, controlFlow, modificationCount);
    cached.addIfAbsent(context);
    return controlFlow;
  }

  private static @NotNull ControlFlowContext createContext(@NotNull ControlFlowOptions options,
                                                           @NotNull ControlFlowPolicy policy,
                                                           @NotNull ControlFlow controlFlow,
                                                           final long modificationCount) {
    return new ControlFlowContext(options, policy, modificationCount, controlFlow);
  }

  private void registerControlFlow(@NotNull PsiElement element,
                                   @NotNull ControlFlow flow,
                                   @NotNull ControlFlowOptions options,
                                   @NotNull ControlFlowPolicy policy) {
    final long modificationCount = element.getManager().getModificationTracker().getModificationCount();
    ControlFlowContext controlFlowContext = createContext(options, policy, flow, modificationCount);

    ConcurrentList<ControlFlowContext> cached = getOrCreateCachedFlowsForElement(element);
    cached.addIfAbsent(controlFlowContext);
  }

  private @NotNull ConcurrentList<ControlFlowContext> getOrCreateCachedFlowsForElement(@NotNull PsiElement element) {
    return cachedFlows.computeIfAbsent(element, __ -> ContainerUtil.createConcurrentList());
  }

  @Override
  public void dispose() {

  }
}

