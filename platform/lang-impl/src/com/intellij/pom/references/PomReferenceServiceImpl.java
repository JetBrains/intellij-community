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
package com.intellij.pom.references;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.patterns.CaseInsensitiveValuePatternCondition;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiNamePatternCondition;
import com.intellij.patterns.ValuePatternCondition;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.NamedObjectProviderBinding;
import com.intellij.psi.impl.source.resolve.reference.SimpleProviderBinding;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.CollectionFactory.*;

/**
 * @author peter
 */
public class PomReferenceServiceImpl extends PomReferenceService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.pom.references.PomReferenceServiceImpl");

  private final Map<Class,SimpleProviderBinding<PomReferenceProvider>> myBindingsMap = newTroveMap();
  private final Map<Class, NamedObjectProviderBinding<PomReferenceProvider>> myNamedBindingsMap = newTroveMap();
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final FactoryMap<Class,Set<Class>> myPossibleSupers = new ConcurrentFactoryMap<Class, Set<Class>>() {
    @Override
    protected Set<Class> create(final Class key) {
      final Set<Class> result = newTroveSet();
      final Condition<Class> condition = new Condition<Class>() {
        public boolean value(Class ancestor) {
          return ancestor.isAssignableFrom(key);
        }
      };
      result.addAll(ContainerUtil.findAll(myBindingsMap.keySet(), condition));
      result.addAll(ContainerUtil.findAll(myNamedBindingsMap.keySet(), condition));
      return result;
    }
  };

  private static final Comparator<Trinity<PomReferenceProvider,ProcessingContext,Double>> PRIORITY_COMPARATOR =
    new Comparator<Trinity<PomReferenceProvider, ProcessingContext, Double>>() {
    public int compare(final Trinity<PomReferenceProvider, ProcessingContext, Double> o1,
                       final Trinity<PomReferenceProvider, ProcessingContext, Double> o2) {
      return o2.getThird().compareTo(o1.getThird());
    }
  };

  public PomReferenceServiceImpl() {
    final PsiReferenceRegistrar registrar = new MyPomReferenceRegistrar();
    for (PsiReferenceContributor contributor : PsiReferenceContributor.EP_NAME.getExtensions()) {
      contributor.registerReferenceProviders(registrar);
    }
  }

  @NotNull
  private List<Trinity<PomReferenceProvider,ProcessingContext,Double>> getPairsByElement(@NotNull PsiElement element, @Nullable final Integer offset) {
    List<Trinity<PomReferenceProvider, ProcessingContext, Double>> ret = null;
    for (final Class aClass : assertNotNull(myPossibleSupers.get(element.getClass()))) {
      final SimpleProviderBinding<PomReferenceProvider> simpleBinding = myBindingsMap.get(aClass);
      final NamedObjectProviderBinding<PomReferenceProvider> namedBinding = myNamedBindingsMap.get(aClass);
      if (simpleBinding == null && namedBinding == null) continue;

      if (ret == null) ret = new SmartList<Trinity<PomReferenceProvider, ProcessingContext, Double>>();
      if (simpleBinding != null) {
        simpleBinding.addAcceptableReferenceProviders(element, ret, new PsiReferenceService.Hints(null, offset));
      }
      if (namedBinding != null) {
        namedBinding.addAcceptableReferenceProviders(element, ret, new PsiReferenceService.Hints(null, offset));
      }
    }
    return ret == null ? Collections.<Trinity<PomReferenceProvider, ProcessingContext, Double>>emptyList() : ret;
  }

  private List<PomReference> getReferencesFromProviders(PsiElement context, final Integer offset) {
    ProgressManager.checkCanceled();
    assert context.isValid() : "Invalid context: " + context;

    final List<Trinity<PomReferenceProvider, ProcessingContext, Double>> providers = getPairsByElement(context, offset);
    if (providers.isEmpty()) {
      return Collections.emptyList();
    }
    Collections.sort(providers, PRIORITY_COMPARATOR);

    List<PomReference> result = arrayList();
    Double maxPriority = Double.MAX_VALUE;
    for (Trinity<PomReferenceProvider, ProcessingContext, Double> trinity : providers) {
      if (result.isEmpty()) {
        maxPriority = trinity.third;
      }
      else if (!trinity.getThird().equals(maxPriority)) {
        break;
      }
      
      @SuppressWarnings({"unchecked"}) final PomReferenceProvider<PsiElement> provider = trinity.getFirst();
      for (PomReference reference : provider.getReferencesByElement(context, trinity.getSecond())) {
        if (reference == null) {
          LOG.error(context);
        }
        if (offsetMatches(offset, reference)) {
          result.add(reference);
        }
      }
    }
    return result;
  }

  private static boolean offsetMatches(@Nullable Integer offset, PomReference reference) {
    if (offset == null) {
      return true;
    }

    final TextRange range = reference.getRangeInElement();
    final int intOffset = offset.intValue();
    return range.getStartOffset() <= intOffset && intOffset <= range.getEndOffset();
  }

  @NotNull
  public List<PomReference> findReferencesAt(@NotNull PsiElement element, int offset) {
    return findReferencesAt(element, offset, true);
  }

  @NotNull
  private List<PomReference> findReferencesAt(@NotNull PsiElement element, int offset, final boolean includeNonPom) {
    List<PomReference> result = arrayList();
    result.addAll(findPomReferencesAt(element, offset));

    if (includeNonPom) {
      final PsiReference reference = element.findReferenceAt(offset);
      if (reference instanceof PsiMultiReference) {
        for (final PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
          result.add(new PomPsiReference(psiReference));
        }
      } else if (reference != null) {
        result.add(new PomPsiReference(reference));
      }
    }
    return result;
  }

  private List<PomReference> findPomReferencesAt(PsiElement element, int offset) {
    final PsiElement leaf = element.findElementAt(offset);
    if (leaf == null) {
      return Collections.emptyList();
    }

    final PsiElement firstComposite = leaf.getParent();
    return getReferencesFromProviders(firstComposite, offset - firstComposite.getTextRange().getStartOffset());
  }

  @NotNull
  @Override
  public List<PomReference> getReferences(@NotNull PsiElement element) {
    List<PomReference> result = arrayList();

    result.addAll(getReferencesFromProviders(element, null));

    for (final PsiReference psiReference : element.getReferences()) {
      result.add(new PomPsiReference(psiReference));
    }
    return result;
  }

  public List<PomReference> findReferencesAt(@NotNull Editor editor, int offset) {
    return findReferencesAt(editor, offset, true);
  }

  private List<PomReference> findReferencesAt(@NotNull Editor editor, int offset, final boolean includeNonPom) {
    Project project = editor.getProject();
    if (project == null) return Collections.emptyList();

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return Collections.emptyList();

    offset = TargetElementUtilBase.adjustOffset(document, offset);

    PsiElement element = file instanceof PsiCompiledElement ? ((PsiCompiledElement)file).getMirror() : file;
    return findReferencesAt(element, offset, includeNonPom);
  }

  @Override
  public List<PomTarget> getReferencedTargets(@NotNull Editor editor, int offset) {
    final ArrayList<PomTarget> result = arrayList();

    for (final PomReference pomReference : findReferencesAt(editor, TargetElementUtilBase.adjustOffset(editor.getDocument(), offset), false)) {
      for (PomTarget target : pomReference.multiResolve()) {
        if (target.canNavigate()) {
          result.add(target);
        }
      }
    }
    if (!result.isEmpty()) {
      return result;
    }

    final Project project = editor.getProject();
    final TargetElementUtilBase oldStuff = TargetElementUtilBase.getInstance();
    if (project != null) {
      int flags = oldStuff.getAllAccepted() & ~TargetElementUtilBase.ELEMENT_NAME_ACCEPTED;
      PsiElement targetElement = oldStuff.findTargetElement(editor, flags, offset);
      if (targetElement != null) {
        return Arrays.<PomTarget>asList(new DelegatePsiTarget(targetElement));
      }
    }

    final PsiReference psiReference = TargetElementUtilBase.findReference(editor, offset);
    if (psiReference != null) {
      for (PsiElement psiElement : oldStuff.getTargetCandidates(psiReference)) {
        PsiElement navElement =
          oldStuff.getGotoDeclarationTarget(psiElement, psiElement.getNavigationElement());
        result.add(PomReferenceUtil.convertPsi2Target(navElement));
      }
    }

    return result;
  }

  private class MyPomReferenceRegistrar extends PsiReferenceRegistrar {
    public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern, @NotNull PsiReferenceProvider provider,
                                                                 double priority) {
    }

    public Project getProject() {
      return null;
    }

    @Override
    public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                                 @NotNull PomReferenceProvider<T> provider,
                                                                 double priority) {
      final Class scope = pattern.getCondition().getInitialCondition().getAcceptedClass();
      final PsiNamePatternCondition<?> nameCondition =
        ContainerUtil.findInstance(pattern.getCondition().getConditions(), PsiNamePatternCondition.class);
      if (nameCondition != null) {
        final ValuePatternCondition<String> valueCondition =
          ContainerUtil.findInstance(nameCondition.getNamePattern().getCondition().getConditions(), ValuePatternCondition.class);
        if (valueCondition != null) {
          final Collection<String> strings = valueCondition.getValues();
          registerNamedReferenceProvider(ArrayUtil.toStringArray(strings), nameCondition, scope, true, provider, priority, pattern);
          return;
        }

        final CaseInsensitiveValuePatternCondition ciCondition = ContainerUtil
          .findInstance(nameCondition.getNamePattern().getCondition().getConditions(), CaseInsensitiveValuePatternCondition.class);
        if (ciCondition != null) {
          registerNamedReferenceProvider(ciCondition.getValues(), nameCondition, scope, false, provider, priority, pattern);
          return;
        }
      }

      SimpleProviderBinding<PomReferenceProvider> providerBinding = myBindingsMap.get(scope);
      if (providerBinding == null) {
        myBindingsMap.put(scope, providerBinding = new SimpleProviderBinding<PomReferenceProvider>());
      }
      providerBinding.registerProvider(provider, pattern, priority);
    }

    private void registerNamedReferenceProvider(final String[] names, final PsiNamePatternCondition<?> nameCondition,
                                                final Class scopeClass,
                                                final boolean caseSensitive,
                                                final PomReferenceProvider provider, final double priority, final ElementPattern pattern) {
      NamedObjectProviderBinding<PomReferenceProvider> providerBinding = myNamedBindingsMap.get(scopeClass);

      if (providerBinding == null) {
        myNamedBindingsMap.put(scopeClass, providerBinding = new NamedObjectProviderBinding<PomReferenceProvider>() {
          @Override
          protected String getName(PsiElement position) {
            return nameCondition.getPropertyValue(position);
          }
        });
      }

      providerBinding.registerProvider(names, pattern, caseSensitive, provider, priority);
    }

  }
}
