package com.intellij.lang.injection;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class JavaConcatenationInjectorManager implements ProjectComponent {
  public static final ExtensionPointName<ConcatenationAwareInjector> CONCATENATION_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.concatenationAwareInjector");
  private final AtomicReference<MultiHostInjector> myRegisteredConcatenationAdapter = new AtomicReference<MultiHostInjector>();
  private final InjectedLanguageManager myInjectedLanguageManager;

  public JavaConcatenationInjectorManager(Project project, InjectedLanguageManager injectedLanguageManager) {
    myInjectedLanguageManager = injectedLanguageManager;
    final ExtensionPoint<ConcatenationAwareInjector> concatPoint = Extensions.getArea(project).getExtensionPoint(CONCATENATION_INJECTOR_EP_NAME);
    concatPoint.addExtensionPointListener(new ExtensionPointListener<ConcatenationAwareInjector>() {
      public void extensionAdded(ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerConcatenationInjector(injector);
      }

      public void extensionRemoved(ConcatenationAwareInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterConcatenationInjector(injector);
      }
    });
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "JavaConcatenationInjectorManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public static JavaConcatenationInjectorManager getInstance(final Project project) {
    return project.getComponent(JavaConcatenationInjectorManager.class);
  }

  private class Concatenation2InjectorAdapter implements MultiHostInjector {
    public void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement context) {
      if (myConcatenationInjectors.isEmpty()) return;
      PsiElement element = context;
      PsiElement parent = context.getParent();
      while (parent instanceof PsiBinaryExpression) {
        element = parent;
        parent = parent.getParent();
      }
      if (element instanceof PsiBinaryExpression) {
        List<PsiElement> operands = new ArrayList<PsiElement>();
        collectOperands(element, operands);
        PsiElement[] elements = operands.toArray(new PsiElement[operands.size()]);
        tryInjectors(injectionPlacesRegistrar, elements);
      }
      else {
        tryInjectors(injectionPlacesRegistrar, context);
      }
    }

    private void collectOperands(PsiElement expression, List<PsiElement> operands) {
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        collectOperands(binaryExpression.getLOperand(), operands);
        collectOperands(binaryExpression.getROperand(), operands);
      }
      else if (expression != null) {
        operands.add(expression);
      }
    }

    void tryInjectors(MultiHostRegistrar registrar, PsiElement... elements) {
      for (ConcatenationAwareInjector concatenationInjector : myConcatenationInjectors) {
        concatenationInjector.getLanguagesToInject(registrar, elements);
      }
    }

    @NotNull
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
      return Arrays.asList(PsiLiteralExpression.class);
    }
  }
  private final List<ConcatenationAwareInjector> myConcatenationInjectors = new CopyOnWriteArrayList<ConcatenationAwareInjector>();
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
    if (myConcatenationInjectors.isEmpty()) {
      MultiHostInjector prev = myRegisteredConcatenationAdapter.getAndSet(null);
      if (prev != null) {
        myInjectedLanguageManager.unregisterMultiHostInjector(prev);
      }
    }
    else {
      MultiHostInjector adapter = new Concatenation2InjectorAdapter();
      if (myRegisteredConcatenationAdapter.compareAndSet(null, adapter)) {
        myInjectedLanguageManager.registerMultiHostInjector(adapter);
      }
    }
  }
}
