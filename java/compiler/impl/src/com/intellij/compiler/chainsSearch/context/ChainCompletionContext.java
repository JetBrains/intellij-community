/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch.context;

import com.intellij.compiler.backwardRefs.MethodIncompleteSignature;
import com.intellij.compiler.chainsSearch.MethodChainsSearchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChainCompletionContext {
  private static final String[] WIDE_USED_CLASS_NAMES = new String [] {CommonClassNames.JAVA_LANG_STRING,
                                                                  CommonClassNames.JAVA_LANG_OBJECT,
                                                                  CommonClassNames.JAVA_LANG_CLASS};
  private static final Set<String> WIDE_USED_SHORT_NAMES = ContainerUtil.set("String", "Object", "Class");

  @NotNull
  private final ChainSearchTarget myTarget;
  @NotNull
  private final List<PsiNamedElement> myContextElements;
  @NotNull
  private final List<PsiNamedElement> myContextStrings;
  @NotNull
  private final PsiElement myContext;
  @NotNull
  private final GlobalSearchScope myResolveScope;
  @NotNull
  private final Project myProject;
  @NotNull
  private final PsiResolveHelper myResolveHelper;
  @NotNull
  private final FactoryMap<MethodIncompleteSignature, PsiClass> myQualifierClassResolver;
  @NotNull
  private final FactoryMap<MethodIncompleteSignature, PsiMethod[]> myResolver;

  public ChainCompletionContext(@NotNull ChainSearchTarget target,
                                @NotNull List<PsiNamedElement> contextElements,
                                @NotNull List<PsiNamedElement> contextStrings,
                                @NotNull PsiElement context) {
    myTarget = target;
    myContextElements = contextElements;
    myContextStrings = contextStrings;
    myContext = context;
    myResolveScope = context.getResolveScope();
    myProject = context.getProject();
    myResolveHelper = PsiResolveHelper.SERVICE.getInstance(myProject);
    myQualifierClassResolver = new FactoryMap<MethodIncompleteSignature, PsiClass>() {
      @Nullable
      @Override
      protected PsiClass create(MethodIncompleteSignature sign) {
        return sign.resolveQualifier(myProject, myResolveScope, accessValidator());
      }
    };
    myResolver = new FactoryMap<MethodIncompleteSignature, PsiMethod[]>() {
      @NotNull
      @Override
      protected PsiMethod[] create(MethodIncompleteSignature sign) {
        return sign.resolve(myProject, myResolveScope, accessValidator());
      }
    };
  }

  @NotNull
  public ChainSearchTarget getTarget() {
    return myTarget;
  }

  public boolean contains(@Nullable PsiType type) {
    if (type == null) return false;
    Set<PsiType> types = getContextTypes();
    if (types.contains(type)) return true;
    for (PsiType contextType : types) {
      if (type.isAssignableFrom(contextType)) {
        return true;
      }
    }
    return false;
  }

  public PsiFile getContextFile() {
    return myContext.getContainingFile();
  }

  @NotNull
  public Set<PsiType> getContextTypes() {
    return myContextElements.stream().map(ChainCompletionContext::getType).collect(Collectors.toSet());
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public PsiElement findRelevantStringInContext(String stringParameterName) {
    String sanitizedTarget = MethodChainsSearchUtil.sanitizedToLowerCase(stringParameterName);
    return myContextStrings.stream().filter(e -> {
      String name = e.getName();
      return name != null && MethodChainsSearchUtil.isSimilar(sanitizedTarget, name);
    }).findFirst().orElse(null);
  }

  public boolean hasQualifier(@Nullable PsiClass targetClass) {
    return getQualifiers(targetClass).findAny().isPresent();
  }

  public Stream<PsiNamedElement> getQualifiers(@Nullable PsiClass targetClass) {
    if (targetClass == null) return Stream.empty();
    return getQualifiers(JavaPsiFacade.getInstance(myProject).getElementFactory().createType(targetClass));
  }

  public Stream<PsiNamedElement> getQualifiers(@NotNull PsiType targetType) {
    return myContextElements.stream().filter(e -> {
      PsiType elementType = getType(e);
      return elementType != null && targetType.isAssignableFrom(elementType);
    });
  }

  @Nullable
  public PsiClass resolveQualifierClass(MethodIncompleteSignature sign) {
    return myQualifierClassResolver.get(sign);
  }

  @NotNull
  public PsiMethod[] resolve(MethodIncompleteSignature sign) {
    return myResolver.get(sign);
  }

  private Predicate<PsiMember> accessValidator() {
    return m -> myResolveHelper.isAccessible(m, myContext, null);
  }

  @Nullable
  public static ChainCompletionContext createContext(@Nullable PsiType targetType,
                                                     @Nullable PsiElement containingElement, boolean suggestIterators) {
    if (containingElement == null) return null;
    ChainSearchTarget target = ChainSearchTarget.create(targetType);
    if (target == null) return null;
    if (suggestIterators) {
      target = target.toIterators();
    }

    ContextProcessor processor = new ContextProcessor(null, containingElement.getProject(), containingElement);
    PsiScopesUtil.treeWalkUp(processor, containingElement, containingElement.getContainingFile());
    List<PsiNamedElement> contextElements = processor.getContextElements();
    List<PsiNamedElement> contextStrings = processor.getContextStrings();

    return new ChainCompletionContext(target, contextElements, contextStrings, containingElement);
  }

  private static class ContextProcessor extends BaseScopeProcessor implements ElementClassHint {
    private final List<PsiNamedElement> myContextElements = new SmartList<>();
    private final List<PsiNamedElement> myContextStrings = new SmartList<>();
    private final PsiVariable myCompletionVariable;
    private final PsiResolveHelper myResolveHelper;
    private final PsiElement myPlace;

    private ContextProcessor(@Nullable PsiVariable variable,
                             @NotNull Project project,
                             @NotNull PsiElement place) {
      myCompletionVariable = variable;
      myResolveHelper = PsiResolveHelper.SERVICE.getInstance(project);
      myPlace = place;
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      return kind == DeclarationKind.ENUM_CONST ||
             kind == DeclarationKind.FIELD ||
             kind == DeclarationKind.METHOD ||
             kind == DeclarationKind.VARIABLE;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if ((!(element instanceof PsiMethod) || PropertyUtil.isSimplePropertyAccessor((PsiMethod)element)) &&
          (!(element instanceof PsiMember) || myResolveHelper.isAccessible((PsiMember)element, myPlace, null))) {
        PsiType type = getType(element);
        if (type == null) {
          return false;
        }
        if (isWidelyUsed(type)) {
          if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            myContextStrings.add((PsiNamedElement)element);
          }
          return false;
        }
        myContextElements.add((PsiNamedElement)element);
      }
      return true;
    }

    @Override
    public <T> T getHint(@NotNull Key<T> hintKey) {
      if (hintKey == ElementClassHint.KEY) {
        //noinspection unchecked
        return (T)this;
      }
      return super.getHint(hintKey);
    }

    @NotNull
    public List<PsiNamedElement> getContextElements() {
      myContextElements.remove(myCompletionVariable);
      return myContextElements;
    }

    @NotNull
    public List<PsiNamedElement> getContextStrings() {
      return myContextStrings;
    }
  }

  @Nullable
  private static PsiType getType(PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getReturnType();
    }
    throw new AssertionError(element);
  }

  public static boolean isWidelyUsed(@NotNull PsiType type) {
    type = type.getDeepComponentType();
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;
    if (WIDE_USED_SHORT_NAMES.contains(((PsiClassType)type).getClassName())) return false;
    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return false;
    final String qName = resolvedClass.getQualifiedName();
    if (qName == null) return false;
    for (String name : WIDE_USED_CLASS_NAMES) {
      if (name.equals(qName)) {
        return true;
      }
    }
    return false;
  }
}