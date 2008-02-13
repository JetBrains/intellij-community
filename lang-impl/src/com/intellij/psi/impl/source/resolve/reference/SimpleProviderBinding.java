package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
public class SimpleProviderBinding implements ProviderBinding {
  private final List<Trinity<PsiReferenceProvider,ElementPattern,Double>> myProviderPairs = new CopyOnWriteArrayList<Trinity<PsiReferenceProvider,ElementPattern,Double>>();

  public void registerProvider(PsiReferenceProvider provider,ElementPattern pattern, double priority){
    myProviderPairs.add(Trinity.create(provider, pattern, priority));
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> list) {
    for(Trinity<PsiReferenceProvider,ElementPattern,Double> trinity:myProviderPairs) {
      final ProcessingContext context = new ProcessingContext();
      if (trinity.second.accepts(position, context)) {
        list.add(Trinity.create(trinity.first, context, trinity.third));
      }
    }
  }

  public void unregisterProvider(final PsiReferenceProvider provider) {
    for (final Trinity<PsiReferenceProvider, ElementPattern, Double> trinity : new ArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>>(myProviderPairs)) {
      if (trinity.first.equals(provider)) {
        myProviderPairs.remove(trinity);
      }
    }
  }

}
