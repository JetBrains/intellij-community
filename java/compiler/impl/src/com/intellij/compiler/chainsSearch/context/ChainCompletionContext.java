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

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.chainsSearch.MethodIncompleteSignature;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.LightRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChainCompletionContext {
  private static final String[] WIDELY_USED_CLASS_NAMES = new String [] {CommonClassNames.JAVA_LANG_STRING,
                                                                  CommonClassNames.JAVA_LANG_OBJECT,
                                                                  CommonClassNames.JAVA_LANG_CLASS};
  private static final Set<String> WIDELY_USED_SHORT_NAMES = ContainerUtil.set("String", "Object", "Class");

  @NotNull
  private final ChainSearchTarget myTarget;
  @NotNull
  private final List<PsiNamedElement> myContextElements;
  @NotNull
  private final PsiElement myContext;
  @NotNull
  private final GlobalSearchScope myResolveScope;
  @NotNull
  private final Project myProject;
  @NotNull
  private final PsiResolveHelper myResolveHelper;
  @NotNull
  private final Map<MethodIncompleteSignature, PsiClass> myQualifierClassResolver;
  @NotNull
  private final Map<MethodIncompleteSignature, PsiMethod[]> myResolver;

  private final NotNullLazyValue<Set<LightRef>> myContextClassReferences = new NotNullLazyValue<Set<LightRef>>() {
    @NotNull
    @Override
    protected Set<LightRef> compute() {
      CompilerReferenceServiceEx referenceServiceEx = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(myProject);
      return getContextTypes()
        .stream()
        .map(PsiUtil::resolveClassInType)
        .filter(Objects::nonNull)
        .map(c -> ClassUtil.getJVMClassName(c))
        .filter(Objects::nonNull)
        .mapToInt(c -> referenceServiceEx.getNameId(c))
        .filter(n -> n != 0)
        .mapToObj(n -> new LightRef.JavaLightClassRef(n)).collect(Collectors.toSet());
    }
  };

  public ChainCompletionContext(@NotNull ChainSearchTarget target,
                                @NotNull List<PsiNamedElement> contextElements,
                                @NotNull PsiElement context) {
    myTarget = target;
    myContextElements = contextElements;
    myContext = context;
    myResolveScope = context.getResolveScope();
    myProject = context.getProject();
    myResolveHelper = PsiResolveHelper.SERVICE.getInstance(myProject);
    myQualifierClassResolver = FactoryMap.createMap(sign -> sign.resolveQualifier(myProject, myResolveScope, accessValidator()));
    myResolver = FactoryMap.createMap(sign -> sign.resolve(myProject, myResolveScope, accessValidator()));
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

  @NotNull
  public PsiElement getContextPsi() {
    return myContext;
  }

  public PsiFile getContextFile() {
    return myContext.getContainingFile();
  }

  @NotNull
  public Set<PsiType> getContextTypes() {
    return myContextElements.stream().map(ChainCompletionContext::getType).collect(Collectors.toSet());
  }

  @NotNull
  public Set<LightRef> getContextClassReferences() {
    return myContextClassReferences.getValue();
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }

  @NotNull
  public Project getProject() {
    return myProject;
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

    Set<? extends PsiVariable> excludedVariables = getEnclosingLocalVariables(containingElement);
    ContextProcessor processor = new ContextProcessor(null, containingElement.getProject(), containingElement, excludedVariables);
    PsiScopesUtil.treeWalkUp(processor, containingElement, containingElement.getContainingFile());
    List<PsiNamedElement> contextElements = processor.getContextElements();

    return new ChainCompletionContext(target, contextElements, containingElement);
  }

  @NotNull
  private static Set<? extends PsiVariable> getEnclosingLocalVariables(@NotNull PsiElement place) {
    Set<PsiLocalVariable> result = new THashSet<>();
    if (place instanceof PsiLocalVariable) result.add((PsiLocalVariable)place);
    PsiElement parent = place.getParent();
    while (parent != null) {
      if (parent instanceof PsiFileSystemItem) break;
      if (parent instanceof PsiLocalVariable && PsiTreeUtil.isAncestor(((PsiLocalVariable)parent).getInitializer(), place, false)) {
        result.add((PsiLocalVariable)parent);
      }
      parent = parent.getParent();
    }
    return result;
  }

  private static class ContextProcessor extends BaseScopeProcessor implements ElementClassHint {
    private final List<PsiNamedElement> myContextElements = new SmartList<>();
    private final PsiVariable myCompletionVariable;
    private final PsiResolveHelper myResolveHelper;
    private final PsiElement myPlace;
    private final Set<? extends PsiVariable> myExcludedVariables;

    private ContextProcessor(@Nullable PsiVariable variable,
                             @NotNull Project project,
                             @NotNull PsiElement place,
                             @NotNull Set<? extends PsiVariable> excludedVariables) {
      myCompletionVariable = variable;
      myResolveHelper = PsiResolveHelper.SERVICE.getInstance(project);
      myPlace = place;
      myExcludedVariables = excludedVariables;
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
          (!(element instanceof PsiVariable) || !myExcludedVariables.contains(element)) &&
          (!(element instanceof PsiMember) || myResolveHelper.isAccessible((PsiMember)element, myPlace, null))) {
        PsiType type = getType(element);
        if (type == null) {
          return false;
        }
        if (isWidelyUsed(type)) {
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
  }

  @Nullable
  private static PsiType getType(PsiElement element) {
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    if (element instanceof PsiMethod) {
      return ((PsiMethod)element).getReturnType();
    }
    return null;
  }

  public static boolean isWidelyUsed(@NotNull PsiType type) {
    type = type.getDeepComponentType();
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;
    if (WIDELY_USED_SHORT_NAMES.contains(((PsiClassType)type).getClassName())) return false;
    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return false;
    final String qName = resolvedClass.getQualifiedName();
    if (qName == null) return false;
    for (String name : WIDELY_USED_CLASS_NAMES) {
      if (name.equals(qName)) {
        return true;
      }
    }
    return false;
  }
}