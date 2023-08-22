// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch.context;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.chainsSearch.MethodCall;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerRef;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ChainCompletionContext {
  private static final String[] WIDELY_USED_CLASS_NAMES = new String[]{
    CommonClassNames.JAVA_LANG_STRING,
    CommonClassNames.JAVA_LANG_OBJECT,
    CommonClassNames.JAVA_LANG_CLASS
  };
  private static final Set<String> WIDELY_USED_SHORT_NAMES = Set.of("String", "Object", "Class");

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
  private final Int2ObjectMap<PsiClass> myQualifierClassResolver;
  @NotNull
  private final Map<MethodCall, PsiMethod[]> myResolver;
  @NotNull
  private final CompilerReferenceServiceEx myRefService;

  private final NotNullLazyValue<Set<CompilerRef>> myContextClassReferences;

  private ChainCompletionContext(@NotNull ChainSearchTarget target,
                                 @NotNull List<PsiNamedElement> contextElements,
                                 @NotNull PsiElement context,
                                 @NotNull CompilerReferenceServiceEx compilerReferenceService) {
    myTarget = target;
    myContextElements = contextElements;
    myContext = context;
    myResolveScope = context.getResolveScope();
    myProject = context.getProject();
    myResolveHelper = PsiResolveHelper.getInstance(myProject);
    myQualifierClassResolver = new Int2ObjectOpenHashMap<>();
    myResolver = FactoryMap.create(sign -> sign.resolve());
    myRefService = compilerReferenceService;
    myContextClassReferences = NotNullLazyValue.createValue(() -> {
        Set<CompilerRef> set = new HashSet<>();
        for (PsiType type : getContextTypes()) {
          PsiClass c = PsiUtil.resolveClassInType(type);
          if (c != null) {
            String name = ClassUtil.getJVMClassName(c);
            if (name != null) {
              int n = myRefService.getNameId(name);
              if (n != 0) {
                set.add(new CompilerRef.JavaCompilerClassRef(n));
              }
            }
          }
        }
        return set;
      });
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
  public CompilerReferenceServiceEx getRefService() {
    return myRefService;
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
  public Set<CompilerRef> getContextClassReferences() {
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
    return getQualifiers(JavaPsiFacade.getElementFactory(myProject).createType(targetClass));
  }

  public Stream<PsiNamedElement> getQualifiers(@NotNull PsiType targetType) {
    return myContextElements.stream().filter(e -> {
      PsiType elementType = getType(e);
      return elementType != null && targetType.isAssignableFrom(elementType);
    });
  }

  public @Nullable PsiClass resolvePsiClass(@NotNull CompilerRef.NamedCompilerRef aClass) {
    int nameId = aClass.getName();

    if (myQualifierClassResolver.containsKey(nameId)) {
      return myQualifierClassResolver.get(nameId);
    }
    else {
      PsiClass psiClass = null;
      String name = myRefService.getName(nameId);
      PsiClass resolvedClass = JavaPsiFacade.getInstance(getProject()).findClass(name, myResolveScope);
      if (resolvedClass != null && accessValidator().test(resolvedClass)) {
        psiClass = resolvedClass;
      }
      myQualifierClassResolver.put(nameId, psiClass);
      return psiClass;
    }
  }

  public PsiMethod @NotNull [] resolve(MethodCall sign) {
    return myResolver.get(sign);
  }

  public Predicate<PsiMember> accessValidator() {
    return m -> myResolveHelper.isAccessible(m, myContext, null);
  }

  public static @Nullable ChainCompletionContext createContext(@Nullable PsiType targetType,
                                                               @Nullable PsiElement containingElement,
                                                               boolean suggestIterators) {
    ChainSearchTarget target = containingElement == null ? null : ChainSearchTarget.create(targetType);
    if (target == null) {
      return null;
    }

    CompilerReferenceServiceEx compilerReferenceService = (CompilerReferenceServiceEx)CompilerReferenceService.getInstanceIfEnabled(containingElement.getProject());
    if (compilerReferenceService == null) {
      return null;
    }

    if (suggestIterators) {
      target = target.toIterators();
    }

    Set<? extends PsiVariable> excludedVariables = getEnclosingLocalVariables(containingElement);
    Project project = containingElement.getProject();
    ContextProcessor processor = new ContextProcessor(null, project, containingElement, excludedVariables);
    PsiScopesUtil.treeWalkUp(processor, containingElement, containingElement.getContainingFile());
    List<PsiNamedElement> contextElements = processor.getContextElements();
    return new ChainCompletionContext(target, contextElements, containingElement, compilerReferenceService);
  }

  @NotNull
  private static Set<? extends PsiVariable> getEnclosingLocalVariables(@NotNull PsiElement place) {
    Set<PsiLocalVariable> result = new HashSet<>();
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

  private static final class ContextProcessor implements PsiScopeProcessor, ElementClassHint {
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
      myResolveHelper = PsiResolveHelper.getInstance(project);
      myPlace = place;
      myExcludedVariables = excludedVariables;
    }

    @Override
    public boolean shouldProcess(@NotNull DeclarationKind kind) {
      return kind == DeclarationKind.ENUM_CONST ||
             kind == DeclarationKind.FIELD ||
             kind == DeclarationKind.METHOD ||
             kind == DeclarationKind.VARIABLE;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if ((!(element instanceof PsiMethod) || PropertyUtilBase.isSimplePropertyAccessor((PsiMethod)element)) &&
          (!(element instanceof PsiVariable) || !myExcludedVariables.contains(element)) &&
          (!(element instanceof PsiMember) || myResolveHelper.isAccessible((PsiMember)element, myPlace, null))) {
        PsiType type = getType(element);
        if (type == null) {
          return true;
        }
        if (isWidelyUsed(type)) {
          return true;
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
      return null;
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
    if (type instanceof PsiPrimitiveType) {
      return true;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }

    String className = ((PsiClassType)type).getClassName();
    if (className != null && WIDELY_USED_SHORT_NAMES.contains(className)) {
      return false;
    }

    final PsiClass resolvedClass = ((PsiClassType)type).resolve();
    if (resolvedClass == null) return false;
    final String qName = resolvedClass.getQualifiedName();
    if (qName == null) return false;
    return ArrayUtil.contains(qName, WIDELY_USED_CLASS_NAMES);
  }
}
