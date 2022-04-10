// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class PsiClassImplUtil {
  private static final Logger LOG = Logger.getInstance(PsiClassImplUtil.class);

  private PsiClassImplUtil() { }

  public static PsiField @NotNull [] getAllFields(@NotNull PsiClass aClass) {
    List<PsiField> map = getAllByMap(aClass, MemberType.FIELD);
    return map.toArray(PsiField.EMPTY_ARRAY);
  }

  public static PsiMethod @NotNull [] getAllMethods(@NotNull PsiClass aClass) {
    List<PsiMethod> methods = getAllByMap(aClass, MemberType.METHOD);
    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static PsiClass @NotNull [] getAllInnerClasses(@NotNull PsiClass aClass) {
    List<PsiClass> classes = getAllByMap(aClass, MemberType.CLASS);
    return classes.toArray(PsiClass.EMPTY_ARRAY);
  }

  /**
   * @return a list of all direct super classes of {@param aClass} concatenated with their super classes, recursively up to the top
   */
  @NotNull
  public static Collection<PsiClass> getAllSuperClassesRecursively(@NotNull PsiClass aClass) {
    return getMap(aClass).myAllSupers;
  }

  @Nullable
  public static PsiField findFieldByName(@NotNull PsiClass aClass, String name, boolean checkBases) {
    List<PsiMember> byMap = findByMap(aClass, name, checkBases, MemberType.FIELD);
    return byMap.isEmpty() ? null : (PsiField)byMap.get(0);
  }

  public static PsiMethod @NotNull [] findMethodsByName(@NotNull PsiClass aClass, String name, boolean checkBases) {
    List<PsiMember> methods = findByMap(aClass, name, checkBases, MemberType.METHOD);
    //noinspection SuspiciousToArrayCall
    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod patternMethod, boolean checkBases) {
    List<PsiMethod> result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
    return result.isEmpty() ? null : result.get(0);
  }

  // ----------------------------- findMethodsBySignature -----------------------------------

  public static PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod patternMethod, boolean checkBases) {
    List<PsiMethod> methods = findMethodsBySignature(aClass, patternMethod, checkBases, false);
    return methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  private static List<PsiMethod> findMethodsBySignature(@NotNull PsiClass aClass,
                                                        @NotNull PsiMethod patternMethod,
                                                        boolean checkBases,
                                                        boolean stopOnFirst) {
    PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
    if (methodsByName.length == 0) return Collections.emptyList();
    List<PsiMethod> methods = new SmartList<>();
    MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (PsiMethod method : methodsByName) {
      PsiClass superClass = method.getContainingClass();
      PsiSubstitutor substitutor = checkBases && !aClass.equals(superClass) && superClass != null ?
                                         TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY) :
                                         PsiSubstitutor.EMPTY;
      MethodSignature signature = method.getSignature(substitutor);
      if (signature.equals(patternSignature)) {
        methods.add(method);
        if (stopOnFirst) {
          break;
        }
      }
    }
    return methods;
  }

  // ----------------------------------------------------------------------------------------

  @Nullable
  public static PsiClass findInnerByName(@NotNull PsiClass aClass, String name, boolean checkBases) {
    List<PsiMember> byMap = findByMap(aClass, name, checkBases, MemberType.CLASS);
    return byMap.isEmpty() ? null : (PsiClass)byMap.get(0);
  }

  public static boolean processAllMembersWithoutSubstitutors(@NotNull PsiClass psiClass, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);

    if ((classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) &&
        !processMembers(state, processor, getMap(psiClass).getAllMembers(MemberType.METHOD, name))) {
      return false;
    }
    if ((classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) &&
        !processMembers(state, processor, getMap(psiClass).getAllMembers(MemberType.FIELD, name))) {
      return false;
    }
    return (classHint != null && !classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) ||
           processMembers(state, processor, getMap(psiClass).getAllMembers(MemberType.CLASS, name));
  }

  private static boolean processMembers(ResolveState state, PsiScopeProcessor processor, PsiMember @NotNull[] members) {
    for (PsiMember member : members) {
      if (!processor.execute(member, state)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static List<PsiMember> findByMap(@NotNull PsiClass aClass, String name, boolean checkBases, @NotNull MemberType type) {
    if (name == null) return Collections.emptyList();

    return checkBases
           ? getMap(aClass).calcMembersByName(type, name, null, null)
           : ContainerUtil.filter(type.getMembers(aClass), member -> name.equals(member.getName()));
  }

  @NotNull
  public static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(@NotNull PsiClass aClass, @NotNull MemberType type) {
    return withSubstitutors(aClass, getMap(aClass).getAllMembers(type, null));
  }

  @NotNull
  private static <T extends PsiMember> List<T> getAllByMap(@NotNull PsiClass aClass, @NotNull MemberType type) {
    //noinspection unchecked
    return Arrays.asList((T[])getMap(aClass).getAllMembers(type, null));
  }

  public enum MemberType {
    CLASS, FIELD, METHOD;

    PsiMember @NotNull[] getMembers(@NotNull PsiClass aClass) {
      switch (this) {
        case METHOD: return aClass.getMethods();
        case CLASS: return aClass.getInnerClasses();
        default: return aClass.getFields();
      }
    }
  }

  private static MemberCache getMap(@NotNull PsiClass aClass) {
    return getMap(aClass, aClass.getResolveScope());
  }

  private static MemberCache getMap(@NotNull PsiClass aClass, @NotNull GlobalSearchScope scope) {
    return CachedValuesManager.getProjectPsiDependentCache(aClass, c ->
      ConcurrentFactoryMap.createMap((GlobalSearchScope s) -> new MemberCache(c, s))).get(scope);
  }

  private static final class ClassIconRequest {
    @NotNull private final PsiClass psiClass;
    private final int flags;
    private final Icon symbolIcon;

    private ClassIconRequest(@NotNull PsiClass psiClass, int flags, Icon symbolIcon) {
      this.psiClass = psiClass;
      this.flags = flags;
      this.symbolIcon = symbolIcon;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClassIconRequest)) return false;

      ClassIconRequest that = (ClassIconRequest)o;

      return flags == that.flags && psiClass.equals(that.psiClass);
    }

    @Override
    public int hashCode() {
      int result = psiClass.hashCode();
      result = 31 * result + flags;
      return result;
    }
  }

  private static final Function<ClassIconRequest, Icon> FULL_ICON_EVALUATOR = r -> {
    if (!r.psiClass.isValid() || r.psiClass.getProject().isDisposed()) {
      return null;
    }

    boolean isLocked = BitUtil.isSet(r.flags, Iconable.ICON_FLAG_READ_STATUS) && !r.psiClass.isWritable();
    Icon symbolIcon = r.symbolIcon != null
                      ? r.symbolIcon
                      : ElementPresentationUtil.getClassIconOfKind(r.psiClass, ElementPresentationUtil.getClassKind(r.psiClass));
    RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(r.psiClass, symbolIcon, ElementPresentationUtil.getFlags(r.psiClass, isLocked));
    Icon result = ElementPresentationUtil.addVisibilityIcon(r.psiClass, r.flags, baseIcon);
    LastComputedIconCache.put(r.psiClass, result, r.flags);
    return result;
  };

  public static Icon getClassIcon(int flags, @NotNull PsiClass aClass) {
    return getClassIcon(flags, aClass, null);
  }

  public static Icon getClassIcon(int flags, @NotNull PsiClass aClass, @Nullable Icon symbolIcon) {
    Icon base = LastComputedIconCache.get(aClass, flags);
    if (base == null) {
      if (symbolIcon == null) {
        symbolIcon = ElementPresentationUtil.getClassIconOfKind(aClass, ElementPresentationUtil.getBasicClassKind(aClass));
      }
      RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(aClass, symbolIcon, 0);
      base = ElementPresentationUtil.addVisibilityIcon(aClass, flags, baseIcon);
    }

    return IconManager.getInstance().createDeferredIcon(base, new ClassIconRequest(aClass, flags, symbolIcon), FULL_ICON_EVALUATOR);
  }

  @NotNull
  public static SearchScope getClassUseScope(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new LocalSearchScope(aClass);
    }
    if (aClass instanceof StubBasedPsiElement) {
      StubElement stubElement = ((StubBasedPsiElement<?>)aClass).getStub();
      if (stubElement instanceof PsiClassStub) {
        PsiClassStub<?> stub = (PsiClassStub<?>)stubElement;
        if (stub instanceof PsiClassStubImpl &&
            ((PsiClassStubImpl<?>)stub).isLocalClassInner()) {
          // at least it's smaller than the containing package
          return new LocalSearchScope(aClass.getContainingFile());
        }
      }
    }
    else {
      PsiElement parent = aClass.getParent();
      if (parent instanceof PsiDeclarationStatement) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiCodeBlock) {
          return new LocalSearchScope(grandParent);
          // Actually: The scope of a local class or interface declaration immediately enclosed by a block is the rest
          // of the immediately enclosing block, including the local class or interface declaration itself (jls-6.3).
        }
      }
    }
    GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(aClass);
    PsiFile file = aClass.getContainingFile();
    if (PsiImplUtil.isInServerPage(file)) return maximalUseScope;
    SearchScope searchScope = PsiSearchScopeUtil.USE_SCOPE_KEY.get(file);
    if (searchScope != null) return searchScope;
    PsiClass containingClass = aClass.getContainingClass();
    if (aClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      return containingClass == null ? maximalUseScope : containingClass.getUseScope();
    }
    else if (aClass.hasModifierProperty(PsiModifier.PRIVATE) || aClass instanceof PsiTypeParameter) {
      PsiClass topClass = PsiUtil.getTopLevelClass(aClass);
      return new LocalSearchScope(topClass == null ? aClass.getContainingFile() : topClass);
    }
    else {
      PsiPackage aPackage = null;
      if (file instanceof PsiJavaFile) {
        aPackage = JavaPsiFacade.getInstance(aClass.getProject()).findPackage(((PsiJavaFile)file).getPackageName());
      }

      if (aPackage == null) {
        PsiDirectory dir = file.getContainingDirectory();
        if (dir != null) {
          aPackage = JavaDirectoryService.getInstance().getPackage(dir);
        }
      }

      if (aPackage != null) {
        SearchScope scope = PackageScope.packageScope(aPackage, false);
        scope = scope.intersectWith(maximalUseScope);
        return scope;
      }

      return new LocalSearchScope(file);
    }
  }

  public static boolean isMainOrPremainMethod(@NotNull PsiMethod method) {
    String name = method.getName();
    if (!("main".equals(name) || "premain".equals(name) || "agentmain".equals(name))) return false;
    if (!PsiType.VOID.equals(method.getReturnType())) return false;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    try {
      MethodSignature main = createSignatureFromText(factory, "void main(String[] args);");
      if (MethodSignatureUtil.areSignaturesEqual(signature, main)) return true;
      MethodSignature premain = createSignatureFromText(factory, "void premain(String args, java.lang.instrument.Instrumentation i);");
      if (MethodSignatureUtil.areSignaturesEqual(signature, premain)) return true;
      MethodSignature agentmain = createSignatureFromText(factory, "void agentmain(String args, java.lang.instrument.Instrumentation i);");
      if (MethodSignatureUtil.areSignaturesEqual(signature, agentmain)) return true;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return false;
  }

  @NotNull
  private static MethodSignature createSignatureFromText(@NotNull PsiElementFactory factory, @NotNull String text) {
    return factory.createMethodFromText(text, null).getSignature(PsiSubstitutor.EMPTY);
  }

  private static class MemberCache {
    private final @NotNull List<PsiClass> myAllSupers;
    private final ConcurrentMap<MemberType, PsiMember[]> myAllMembers;

    MemberCache(@NotNull PsiClass psiClass, @NotNull GlobalSearchScope scope) {
      myAllSupers = JBTreeTraverser
        .from((PsiClass c) -> ContainerUtil.mapNotNull(c.getSupers(), s -> PsiSuperMethodUtil.correctClassByScope(s, scope)))
        .unique()
        .withRoot(psiClass)
        .toList();
      myAllMembers = ConcurrentFactoryMap.createMap(
        type -> StreamEx.of(myAllSupers).flatArray(type::getMembers).filter(e -> !skipInvalid(e)).toArray(PsiMember.EMPTY_ARRAY));
    }

    @NotNull List<PsiMember> calcMembersByName(@NotNull MemberType type, @NotNull String name, PsiClass psiClass, PsiElement context) {
      List<PsiMember> result = null;
      for (PsiClass eachSuper : myAllSupers) {
        if (type == MemberType.METHOD) {
          PsiMethod[] methods = eachSuper.findMethodsByName(name, false);
          if (methods.length > 0) {
            if (result == null) result = new ArrayList<>();
            Collections.addAll(result, methods);
          }
        }
        else {
          PsiMember member = type == MemberType.CLASS
                             ? eachSuper.findInnerClassByName(name, false)
                             : eachSuper.findFieldByName(name, false);
          if (member != null) {
            if (result == null) result = new ArrayList<>();
            result.add(member);
          }
        }
      }
      if (type == MemberType.METHOD && psiClass != null && context != null) {
        List<PsiExtensionMethod> methods = PsiAugmentProvider.collectExtensionMethods(psiClass, name, context);
        if (!methods.isEmpty()) {
          if (result == null) result = new ArrayList<>();
          result.addAll(methods);
        }
      }
      return result == null ? Collections.emptyList() : ContainerUtil.filter(result, e -> !skipInvalid(e));
    }

    PsiMember[] getAllMembers(@NotNull MemberType type, @Nullable String name) {
      return name == null ? myAllMembers.get(type) : calcMembersByName(type, name, null, null).toArray(PsiMember.EMPTY_ARRAY);
    }
  }

  private static boolean skipInvalid(@NotNull PsiElement element) {
    try {
      PsiUtilCore.ensureValid(element);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return true;
    }
    return false;
  }

  public static boolean processDeclarationsInClass(@NotNull PsiClass aClass,
                                                   @NotNull PsiScopeProcessor processor,
                                                   @NotNull ResolveState state,
                                                   @Nullable Set<PsiClass> visited,
                                                   PsiElement last,
                                                   @NotNull PsiElement place,
                                                   @NotNull LanguageLevel languageLevel,
                                                   boolean isRaw) {
    return processDeclarationsInClass(aClass, processor, state, visited, last, place, languageLevel, isRaw, place.getResolveScope());
  }

  private static boolean processDeclarationsInClass(@NotNull PsiClass aClass,
                                                    @NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    @Nullable Set<PsiClass> visited,
                                                    PsiElement last,
                                                    @NotNull PsiElement place,
                                                    @NotNull LanguageLevel languageLevel,
                                                    boolean isRaw,
                                                    @NotNull GlobalSearchScope resolveScope) {
    if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList && aClass.getModifierList() == last) {
      return true;
    }
    if (visited != null && visited.contains(aClass)) return true;

    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    isRaw = isRaw || PsiUtil.isRawSubstitutor(aClass, substitutor);

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    if (name != null) {
      return processCachedMembersByName(aClass, processor, state, visited, last, place, isRaw, substitutor,
                                        name, languageLevel, resolveScope);
    }
    return processClassMembersWithAllNames(aClass, processor, state, visited, last, place, isRaw, languageLevel, resolveScope);
  }

  private static boolean processCachedMembersByName(@NotNull PsiClass aClass,
                                                    @NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    @Nullable Set<? super PsiClass> visited,
                                                    PsiElement last,
                                                    @NotNull PsiElement place,
                                                    boolean isRaw,
                                                    @NotNull PsiSubstitutor substitutor,
                                                    String name,
                                                    @NotNull LanguageLevel languageLevel,
                                                    @NotNull GlobalSearchScope resolveScope) {
    java.util.function.Function<PsiMember, PsiSubstitutor> finalSubstitutor = new java.util.function.Function<PsiMember, PsiSubstitutor>() {
      final ScopedClassHierarchy hierarchy = ScopedClassHierarchy.getHierarchy(aClass, resolveScope);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());

      @Override
      public PsiSubstitutor apply(PsiMember member) {
        PsiSubstitutor finalSubstitutor = member.hasModifierProperty(PsiModifier.STATIC) ? substitutor : obtainSubstitutor(member);
        return member instanceof PsiMethod ? checkRaw(isRaw, factory, (PsiMethod)member, finalSubstitutor) : finalSubstitutor;
      }

      private PsiSubstitutor obtainSubstitutor(PsiMember member) {
        PsiClass containingClass = Objects.requireNonNull(member.getContainingClass());
        PsiSubstitutor superSubstitutor = ObjectUtils.notNull(hierarchy.getSuperMembersSubstitutor(containingClass, languageLevel),
                                                              PsiSubstitutor.EMPTY);
        return obtainFinalSubstitutor(containingClass, superSubstitutor, aClass, substitutor, factory, languageLevel);
      }
    };

    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      PsiField fieldByName = aClass.findFieldByName(name, false);
      if (fieldByName != null) {
        if (!skipInvalid(fieldByName)) {
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          if (!processor.execute(fieldByName, state)) return false;
        }
      }
      else {
        List<PsiMember> list = getMap(aClass, resolveScope).calcMembersByName(MemberType.FIELD, name, aClass, place);
        if (!list.isEmpty()) {
          boolean resolved = false;
          for (PsiMember candidateField : list) {
            PsiClass containingClass = candidateField.getContainingClass();
            if (skipInvalid(candidateField)) {
              continue;
            }
            if (containingClass == null) {
              PsiElement parent = candidateField.getParent();
              LOG.error("No class for field " + candidateField.getName() + " of " + candidateField.getClass() +
                        ", parent " + parent + " of " + (parent == null ? null : parent.getClass()));
              continue;
            }

            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
            if (!processor.execute(candidateField, state.put(PsiSubstitutor.KEY, finalSubstitutor.apply(candidateField)))) {
              resolved = true;
            }
          }
          if (resolved) return false;
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (last != null && last.getContext() == aClass) {
        if (last instanceof PsiClass) {
          if (!processor.execute(last, state)) return false;
        }
        // Parameters
        PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, state, last, place)) return false;
      }
      if (!(last instanceof PsiReferenceList)) {
        PsiClass classByName = aClass.findInnerClassByName(name, false);
        if (classByName != null) {
          if (!skipInvalid(classByName)) {
            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
            if (!processor.execute(classByName, state)) return false;
          }
        }
        else {
          List<PsiMember> list = getMap(aClass, resolveScope).calcMembersByName(MemberType.CLASS, name, aClass, place);
          if (!list.isEmpty()) {
            boolean resolved = false;
            for (PsiMember inner : list) {
              if (skipInvalid(inner)) continue;

              PsiClass containingClass = inner.getContainingClass();
              if (containingClass != null) {
                processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
                if (!processor.execute(inner, state.put(PsiSubstitutor.KEY, finalSubstitutor.apply(inner)))) {
                  resolved = true;
                }
              }
            }
            if (resolved) return false;
          }
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      if (processor instanceof MethodResolverProcessor) {
        MethodResolverProcessor methodResolverProcessor = (MethodResolverProcessor)processor;
        if (methodResolverProcessor.isConstructor()) {
          PsiMethod[] constructors = aClass.getConstructors();
          methodResolverProcessor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          for (PsiMethod constructor : constructors) {
            if (!skipInvalid(constructor) && !methodResolverProcessor.execute(constructor, state)) return false;
          }
          return true;
        }
      }
      List<PsiMember> list = getMap(aClass, resolveScope).calcMembersByName(MemberType.METHOD, name, aClass, place);
      if (!list.isEmpty()) {
        boolean resolved = false;
        for (PsiMember candidate : list) {
          ProgressIndicatorProvider.checkCanceled();
          PsiMethod candidateMethod = (PsiMethod)candidate;
          if (skipInvalid(candidateMethod)) continue;

          if (processor instanceof MethodResolverProcessor) {
            if (candidateMethod.isConstructor() != ((MethodResolverProcessor)processor).isConstructor()) continue;
          }
          PsiClass containingClass = candidateMethod.getContainingClass();
          if (containingClass == null || visited != null && visited.contains(containingClass)) {
            continue;
          }

          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
          if (!processor.execute(candidateMethod, state.put(PsiSubstitutor.KEY, finalSubstitutor.apply(candidateMethod)))) {
            resolved = true;
          }
        }
        if (resolved) return false;

        if (visited != null) {
          for (PsiMember aList : list) {
            visited.add(aList.getContainingClass());
          }
        }
      }
    }
    return true;
  }

  private static PsiSubstitutor checkRaw(boolean isRaw,
                                         @NotNull PsiElementFactory factory,
                                         @NotNull PsiMethod candidateMethod,
                                         @NotNull PsiSubstitutor substitutor) {
    //4.8-2. Raw Types and Inheritance
    //certain members of a raw type are not erased,
    //namely static members whose types are parameterized, and members inherited from a non-generic supertype.
    if (isRaw && !candidateMethod.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = candidateMethod.getContainingClass();
      if (containingClass != null && containingClass.hasTypeParameters()) {
        PsiTypeParameter[] methodTypeParameters = candidateMethod.getTypeParameters();
        substitutor = factory.createRawSubstitutor(substitutor, methodTypeParameters);
      }
    }
    return substitutor;
  }

  public static PsiSubstitutor obtainFinalSubstitutor(@NotNull PsiClass candidateClass,
                                                      @NotNull PsiSubstitutor candidateSubstitutor,
                                                      @NotNull PsiClass aClass,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      @NotNull PsiElementFactory elementFactory,
                                                      @NotNull LanguageLevel languageLevel) {
    if (PsiUtil.isRawSubstitutor(aClass, substitutor)) {
      return elementFactory.createRawSubstitutor(candidateClass).putAll(substitutor);
    }
    PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, languageLevel);
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    return ((PsiClassType)type).resolveGenerics().getSubstitutor();
  }

  private static boolean processClassMembersWithAllNames(@NotNull PsiClass aClass,
                                                         @NotNull PsiScopeProcessor processor,
                                                         @NotNull ResolveState state,
                                                         @Nullable Set<PsiClass> visited,
                                                         PsiElement last,
                                                         @NotNull PsiElement place,
                                                         boolean isRaw,
                                                         @NotNull LanguageLevel languageLevel,
                                                         @NotNull GlobalSearchScope resolveScope) {
    ProgressManager.checkCanceled();
    if (visited == null) {
      visited = new HashSet<>();
    }
    if (!visited.add(aClass)) {
      return true;
    }
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      if (!processMembers(state, processor, aClass.getFields())) return false;
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      PsiSubstitutor baseSubstitutor = state.get(PsiSubstitutor.KEY);
      for (PsiMethod method : aClass.getMethods()) {
        PsiSubstitutor finalSubstitutor = checkRaw(isRaw, factory, method, baseSubstitutor);
        ResolveState methodState = finalSubstitutor == baseSubstitutor ? state : state.put(PsiSubstitutor.KEY, finalSubstitutor);
        if (!processor.execute(method, methodState)) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (last != null && last.getContext() == aClass) {
        // Parameters
        PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, ResolveState.initial(), last, place)) return false;
      }

      if (!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList)) {
        if (!processMembers(state, processor, aClass.getInnerClasses())) return false;
      }
    }

    if (last instanceof PsiReferenceList) return true;

    Set<PsiClass> visited1 = visited;
    return processSuperTypes(aClass, state.get(PsiSubstitutor.KEY), factory, languageLevel, resolveScope,
                             (superClass, finalSubstitutor) -> processDeclarationsInClass(superClass, processor, state.put(PsiSubstitutor.KEY, finalSubstitutor), visited1, last, place,
                                                                                                               languageLevel, isRaw, resolveScope));
  }

  @Nullable
  public static <T extends PsiType> T correctType(@Nullable T originalType, @NotNull GlobalSearchScope resolveScope) {
    if (originalType == null || !Registry.is("java.correct.class.type.by.place.resolve.scope")) {
      return originalType;
    }

    return new TypeCorrector(resolveScope).correctType(originalType);
  }

  public static List<PsiClassType.ClassResolveResult> getScopeCorrectedSuperTypes(PsiClass aClass, GlobalSearchScope resolveScope) {
    if (skipInvalid(aClass)) {
      return Collections.emptyList();
    }
    return ScopedClassHierarchy.getHierarchy(aClass, resolveScope).getImmediateSupersWithCapturing();
  }

  static boolean processSuperTypes(@NotNull PsiClass aClass,
                                   PsiSubstitutor substitutor,
                                   @NotNull PsiElementFactory factory,
                                   @NotNull LanguageLevel languageLevel,
                                   GlobalSearchScope resolveScope,
                                   PairProcessor<? super PsiClass, ? super PsiSubstitutor> processor) {
    boolean resolved = false;
    for (PsiClassType.ClassResolveResult superTypeResolveResult : getScopeCorrectedSuperTypes(aClass, resolveScope)) {
      PsiClass superClass = superTypeResolveResult.getElement();
      assert superClass != null;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass,
                                                               substitutor, factory, languageLevel);
      if (!processor.process(superClass, finalSubstitutor)) {
        resolved = true;
      }
    }
    return !resolved;
  }

  @Nullable
  public static PsiClass getSuperClass(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
    }
    if (psiClass.isEnum()) {
      return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_ENUM);
    }
    if (psiClass.isRecord()) {
      return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_RECORD);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass == null || baseClass.isInterface()) return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
      return baseClass;
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return null;

    PsiClassType[] referenceElements = psiClass.getExtendsListTypes();

    if (referenceElements.length == 0) return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);

    PsiClass psiResolved = referenceElements[0].resolve();
    return psiResolved == null ? findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT) : psiResolved;
  }

  @Nullable
  private static PsiClass findSpecialSuperClass(@NotNull PsiClass psiClass, @NotNull String className) {
    return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(className, psiClass.getResolveScope());
  }

  public static PsiClass @NotNull [] getSupers(@NotNull PsiClass psiClass) {
    PsiClass[] supers = getSupersInner(psiClass);
    for (PsiClass aSuper : supers) {
      LOG.assertTrue(aSuper != null);
    }
    return supers;
  }

  private static PsiClass @NotNull [] getSupersInner(@NotNull PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();

    if (psiClass.isInterface()) {
      return resolveClassReferenceList(extendsListTypes, psiClass, true);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)psiClass;
      PsiClassType baseClassReference = psiAnonymousClass.getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass != null) {
        if (baseClass.isInterface()) {
          PsiClass objectClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
          return objectClass != null ? new PsiClass[]{objectClass, baseClass} : new PsiClass[]{baseClass};
        }
        return new PsiClass[]{baseClass};
      }

      PsiClass objectClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
      return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
    }
    if (psiClass instanceof PsiTypeParameter) {
      if (extendsListTypes.length == 0) {
        PsiClass objectClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
        return objectClass != null ? new PsiClass[]{objectClass} : PsiClass.EMPTY_ARRAY;
      }
      return resolveClassReferenceList(extendsListTypes, psiClass, false);
    }

    PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
    PsiClass[] interfaces = resolveClassReferenceList(implementsListTypes, psiClass, false);

    PsiClass superClass = getSuperClass(psiClass);
    if (superClass == null) return interfaces;
    PsiClass[] types = new PsiClass[interfaces.length + 1];
    types[0] = superClass;
    System.arraycopy(interfaces, 0, types, 1, interfaces.length);

    return types;
  }

  public static PsiClassType @NotNull [] getSuperTypes(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassType = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassType.resolve();
      if (baseClass == null || !baseClass.isInterface()) {
        return new PsiClassType[]{baseClassType};
      }
      else {
        PsiClassType objectType = PsiType.getJavaLangObject(psiClass.getManager(), psiClass.getResolveScope());
        return new PsiClassType[]{objectType, baseClassType};
      }
    }

    PsiClassType[] extendsTypes = psiClass.getExtendsListTypes();
    PsiClassType[] implementsTypes = psiClass.getImplementsListTypes();
    boolean hasExtends = extendsTypes.length != 0;
    int extendsListLength = extendsTypes.length + (hasExtends ? 0 : 1);
    PsiClassType[] result = new PsiClassType[extendsListLength + implementsTypes.length];

    System.arraycopy(extendsTypes, 0, result, 0, extendsTypes.length);
    if (!hasExtends) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
        return PsiClassType.EMPTY_ARRAY;
      }
      PsiManager manager = psiClass.getManager();
      PsiClassType objectType = PsiType.getJavaLangObject(manager, psiClass.getResolveScope());
      result[0] = objectType;
    }
    System.arraycopy(implementsTypes, 0, result, extendsListLength, implementsTypes.length);
    return result;
  }

  @NotNull
  private static PsiClassType getAnnotationSuperType(@NotNull PsiClass psiClass, @NotNull PsiElementFactory factory) {
    return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, psiClass.getResolveScope());
  }

  private static PsiClassType getEnumSuperType(@NotNull PsiClass psiClass, @NotNull PsiElementFactory factory) {
    PsiClassType superType;
    PsiClass enumClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_ENUM);
    if (enumClass == null) {
      try {
        superType = (PsiClassType)factory.createTypeFromText(CommonClassNames.JAVA_LANG_ENUM, null);
      }
      catch (IncorrectOperationException e) {
        superType = null;
      }
    }
    else {
      PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], factory.createType(psiClass));
      }
      superType = new PsiImmediateClassType(enumClass, substitutor);
    }
    return superType;
  }

  public static PsiClass @NotNull [] getInterfaces(@NotNull PsiTypeParameter typeParameter) {
    PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
    if (referencedTypes.length == 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> result = new ArrayList<>(referencedTypes.length);
    for (PsiClassType referencedType : referencedTypes) {
      PsiClass psiClass = referencedType.resolve();
      if (psiClass != null && psiClass.isInterface()) {
        result.add(psiClass);
      }
    }
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  public static PsiClass @NotNull [] getInterfaces(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      return resolveClassReferenceList(psiClass.getExtendsListTypes(), psiClass, false);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      return baseClass != null && baseClass.isInterface() ? new PsiClass[]{baseClass} : PsiClass.EMPTY_ARRAY;
    }

    PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
    return resolveClassReferenceList(implementsListTypes, psiClass, false);
  }

  private static PsiClass @NotNull [] resolveClassReferenceList(PsiClassType @NotNull [] listOfTypes,
                                                                @NotNull PsiClass psiClass,
                                                                boolean includeObject) {
    PsiClass objectClass = null;
    if (includeObject) {
      objectClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
      if (objectClass == null) includeObject = false;
    }
    if (listOfTypes.length == 0) {
      if (includeObject) return new PsiClass[]{objectClass};
      return PsiClass.EMPTY_ARRAY;
    }

    int referenceCount = listOfTypes.length;
    if (includeObject) referenceCount++;

    PsiClass[] resolved = new PsiClass[referenceCount];
    int resolvedCount = 0;

    if (includeObject) resolved[resolvedCount++] = objectClass;
    for (PsiClassType reference : listOfTypes) {
      PsiClass refResolved = reference.resolve();
      if (refResolved != null) resolved[resolvedCount++] = refResolved;
    }

    if (resolvedCount < referenceCount) {
      resolved = ArrayUtil.realloc(resolved, resolvedCount,PsiClass.ARRAY_FACTORY);
    }

    return resolved;
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull PsiClass psiClass,
                                                                                            @NotNull String name,
                                                                                            boolean checkBases) {
    if (!checkBases) {
      PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
      List<Pair<PsiMethod, PsiSubstitutor>> ret = new ArrayList<>(methodsByName.length);
      for (PsiMethod method : methodsByName) {
        ret.add(Pair.create(method, PsiSubstitutor.EMPTY));
      }
      return ret;
    }
    List<PsiMember> list = getMap(psiClass).calcMembersByName(MemberType.METHOD, name, null, null);
    if (list.isEmpty()) return Collections.emptyList();
    return withSubstitutors(psiClass, list.toArray(PsiMember.EMPTY_ARRAY));
  }

  @NotNull
  private static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> withSubstitutors(@NotNull PsiClass psiClass, PsiMember[] members) {
    ScopedClassHierarchy hierarchy = ScopedClassHierarchy.getHierarchy(psiClass, psiClass.getResolveScope());
    LanguageLevel level = PsiUtil.getLanguageLevel(psiClass);
    return ContainerUtil.map(members, member -> {
      PsiClass containingClass = member.getContainingClass();
      PsiSubstitutor substitutor = containingClass == null ? null : hierarchy.getSuperMembersSubstitutor(containingClass, level);
      //noinspection unchecked
      return Pair.create((T)member, substitutor == null ? PsiSubstitutor.EMPTY : substitutor);
    });
  }

  public static PsiClassType @NotNull [] getExtendsListTypes(@NotNull PsiClass psiClass) {
    if (psiClass.isEnum()) {
      PsiClassType enumSuperType = getEnumSuperType(psiClass, JavaPsiFacade.getElementFactory(psiClass.getProject()));
      return enumSuperType == null ? PsiClassType.EMPTY_ARRAY : new PsiClassType[]{enumSuperType};
    }
    if (psiClass.isRecord()) {
      PsiClass recordClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_RECORD);
      if (recordClass != null) {
        return new PsiClassType[]{new PsiImmediateClassType(recordClass, PsiSubstitutor.EMPTY)};
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      return new PsiClassType[]{factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_RECORD, psiClass.getResolveScope())};
    }
    if (psiClass.isAnnotationType()) {
      return new PsiClassType[]{getAnnotationSuperType(psiClass, JavaPsiFacade.getElementFactory(psiClass.getProject()))};
    }
    PsiType upperBound = psiClass instanceof PsiTypeParameter ? TypeConversionUtil.getInferredUpperBoundForSynthetic((PsiTypeParameter)psiClass) : null;
    if (upperBound == null && psiClass instanceof PsiTypeParameter) {
      upperBound = ThreadLocalTypes.getElementType(psiClass);
    }
    if (upperBound instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)upperBound).getConjuncts();
      List<PsiClassType> result = new ArrayList<>();
      for (PsiType conjunct : conjuncts) {
        if (conjunct instanceof PsiClassType) {
          result.add((PsiClassType)conjunct);
        }
      }
      return result.toArray(PsiClassType.EMPTY_ARRAY);
    }
    if (upperBound instanceof PsiClassType) {
      return new PsiClassType[] {(PsiClassType)upperBound};
    }
    PsiReferenceList extendsList = psiClass.getExtendsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  public static PsiClassType @NotNull [] getImplementsListTypes(@NotNull PsiClass psiClass) {
    PsiReferenceList extendsList = psiClass.getImplementsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  static boolean isInExtendsList(@NotNull PsiClass psiClass,
                                 @NotNull PsiClass baseClass,
                                 @Nullable String baseName,
                                 @NotNull PsiManager manager) {
    if (psiClass.isEnum()) {
      return CommonClassNames.JAVA_LANG_ENUM.equals(baseClass.getQualifiedName());
    }
    if (psiClass.isAnnotationType()) {
      return CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(baseClass.getQualifiedName());
    }
    PsiType upperBound = psiClass instanceof PsiTypeParameter ? TypeConversionUtil.getInferredUpperBoundForSynthetic((PsiTypeParameter)psiClass) : null;
    if (upperBound == null && psiClass instanceof PsiTypeParameter) {
      upperBound = ThreadLocalTypes.getElementType(psiClass);
    }
    if (upperBound instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)upperBound).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (conjunct instanceof PsiClassType && ((PsiClassType)conjunct).getClassName().equals(baseName) && baseClass.equals(((PsiClassType)conjunct).resolve())) {
          return true;
        }
      }
      return false;
    }
    if (upperBound instanceof PsiClassType) {
      return ((PsiClassType)upperBound).getClassName().equals(baseName) && baseClass.equals(((PsiClassType)upperBound).resolve());
    }

    return isInReferenceList(psiClass.getExtendsList(), baseClass, baseName, manager);
  }

  static boolean isInReferenceList(@Nullable PsiReferenceList list,
                                   @NotNull PsiClass baseClass,
                                   @Nullable String baseName,
                                   @NotNull PsiManager manager) {
    if (list == null) return false;
    if (list instanceof StubBasedPsiElement) {
      StubElement<?> stub = ((StubBasedPsiElement<?>)list).getStub();
      if (stub instanceof PsiClassReferenceListStub && baseName != null) {
        // classStub.getReferencedNames() is cheaper than getReferencedTypes()
        PsiClassReferenceListStub classStub = (PsiClassReferenceListStub)stub;
        String[] names = classStub.getReferencedNames();
        for (int i = 0; i < names.length; i++) {
          String name = PsiNameHelper.getShortClassName(names[i]);
          // baseName=="ArrayList" classStub.getReferenceNames()[i]=="java.util.ArrayList"
          if (name.endsWith(baseName)) {
            PsiClassType[] referencedTypes = classStub.getReferencedTypes();
            PsiClassType type = i >= referencedTypes.length ? null : referencedTypes[i];
            PsiClass resolved = type == null ? null : type.resolve();
            if (manager.areElementsEquivalent(baseClass, resolved)) return true;
          }
        }
        return false;
      }
      if (stub != null) {
        // groovy etc
        for (PsiClassType type : list.getReferencedTypes()) {
          if (Objects.equals(type.getClassName(), baseName) && manager.areElementsEquivalent(baseClass, type.resolve())) {
            return true;
          }
        }
        return false;
      }
    }

    if (list.getLanguage() == JavaLanguage.INSTANCE) {
      // groovy doesn't have list.getReferenceElements()
      for (PsiJavaCodeReferenceElement referenceElement : list.getReferenceElements()) {
        if (Comparing.strEqual(baseName, referenceElement.getReferenceName()) &&
            manager.areElementsEquivalent(baseClass, referenceElement.resolve())) {
          return true;
        }
      }
      return false;
    }

    for (PsiClassType type : list.getReferencedTypes()) {
      if (Objects.equals(type.getClassName(), baseName) && manager.areElementsEquivalent(baseClass, type.resolve())) {
        return true;
      }
    }
    return false;
  }

  public static boolean isClassEquivalentTo(@NotNull PsiClass aClass, PsiElement another) {
    if (aClass == another) return true;
    if (!(another instanceof PsiClass)) return false;
    String name1 = aClass.getName();
    if (name1 == null) return false;
    if (!another.isValid()) return false;
    String name2 = ((PsiClass)another).getName();
    if (name2 == null) return false;
    if (name1.hashCode() != name2.hashCode()) return false;
    if (!name1.equals(name2)) return false;
    String qName1 = aClass.getQualifiedName();
    String qName2 = ((PsiClass)another).getQualifiedName();
    if (qName1 == null || qName2 == null) {
      //noinspection StringEquality
      if (qName1 != qName2) return false;

      if (aClass instanceof PsiTypeParameter && another instanceof PsiTypeParameter) {
        PsiTypeParameter p1 = (PsiTypeParameter)aClass;
        PsiTypeParameter p2 = (PsiTypeParameter)another;

        if (p1.getIndex() != p2.getIndex()) {
          return false;
        }
        if (TypeConversionUtil.areSameFreshVariables(p1, p2)) {
          return true;
        }

        return !Boolean.FALSE.equals(RecursionManager.doPreventingRecursion(Pair.create(p1, p2), true, () ->
          aClass.getManager().areElementsEquivalent(p1.getOwner(), p2.getOwner())));
      }
      else {
        return false;
      }
    }
    if (!qName1.equals(qName2)) {
      return false;
    }

    if (aClass.getOriginalElement().equals(another.getOriginalElement())) {
      return true;
    }

    PsiFile file1 = getOriginalFile(aClass);
    PsiFile file2 = getOriginalFile((PsiClass)another);

    //see com.intellij.openapi.vcs.changes.PsiChangeTracker
    //see com.intellij.psi.impl.PsiFileFactoryImpl#createFileFromText(CharSequence,PsiFile)
    PsiFile original1 = file1.getUserData(PsiFileFactory.ORIGINAL_FILE);
    PsiFile original2 = file2.getUserData(PsiFileFactory.ORIGINAL_FILE);
    if (original1 == original2 && original1 != null || original1 == file2 || original2 == file1 || file1 == file2) {
      return true;
    }

    FileIndexFacade fileIndex = file1.getProject().getService(FileIndexFacade.class);
    FileIndexFacade fileIndex2 = file2.getProject().getService(FileIndexFacade.class);
    VirtualFile vfile1 = file1.getViewProvider().getVirtualFile();
    VirtualFile vfile2 = file2.getViewProvider().getVirtualFile();
    boolean lib1 = fileIndex.isInLibraryClasses(vfile1);
    boolean lib2 = fileIndex2.isInLibraryClasses(vfile2);

    //if copy from another project, fileIndex of correct project should be requested
    return (fileIndex.isInSource(vfile1) || lib1) && (fileIndex2.isInSource(vfile2) || lib2);
  }

  @NotNull
  private static PsiFile getOriginalFile(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file == null) {
      PsiUtilCore.ensureValid(aClass);
      throw new IllegalStateException("No containing file for " + aClass.getLanguage() + " " + aClass.getClass());
    }
    return file.getOriginalFile();
  }

  public static boolean isFieldEquivalentTo(@NotNull PsiField field, PsiElement another) {
    if (!(another instanceof PsiField)) return false;
    String name1 = field.getName();
    if (!another.isValid()) return false;

    String name2 = ((PsiField)another).getName();
    if (!name1.equals(name2)) return false;
    PsiClass aClass1 = field.getContainingClass();
    PsiClass aClass2 = ((PsiField)another).getContainingClass();
    return aClass1 != null && aClass2 != null && field.getManager().areElementsEquivalent(aClass1, aClass2);
  }

  public static boolean isMethodEquivalentTo(@NotNull PsiMethod method1, PsiElement another) {
    if (method1 == another) return true;
    if (!(another instanceof PsiMethod)) return false;
    PsiMethod method2 = (PsiMethod)another;
    if (!another.isValid()) return false;
    if (!method1.getName().equals(method2.getName())) return false;
    PsiClass aClass1 = method1.getContainingClass();
    PsiClass aClass2 = method2.getContainingClass();
    PsiManager manager = method1.getManager();
    if (!(aClass1 != null && aClass2 != null && manager.areElementsEquivalent(aClass1, aClass2))) return false;

    PsiParameter[] parameters1 = method1.getParameterList().getParameters();
    PsiParameter[] parameters2 = method2.getParameterList().getParameters();
    if (parameters1.length != parameters2.length) return false;
    for (int i = 0; i < parameters1.length; i++) {
      PsiParameter parameter1 = parameters1[i];
      PsiParameter parameter2 = parameters2[i];
      PsiType type1 = parameter1.getType();
      PsiType type2 = parameter2.getType();
      if (!compareParamTypes(manager, type1, type2, new HashSet<>())) return false;
    }
    return true;
  }

  private static boolean compareParamTypes(@NotNull PsiManager manager, @NotNull PsiType type1, @NotNull PsiType type2, Set<? super String> visited) {
    if (type1 instanceof PsiArrayType) {
      if (type2 instanceof PsiArrayType) {
        PsiType componentType1 = ((PsiArrayType)type1).getComponentType();
        PsiType componentType2 = ((PsiArrayType)type2).getComponentType();
        if (compareParamTypes(manager, componentType1, componentType2, visited)) return true;
      }
      return false;
    }

    if (!(type1 instanceof PsiClassType) || !(type2 instanceof PsiClassType)) {
      return type1.equals(type2);
    }

    PsiClass class1 = ((PsiClassType)type1).resolve();
    PsiClass class2 = ((PsiClassType)type2).resolve();
    visited.add(type1.getCanonicalText());
    visited.add(type2.getCanonicalText());

    if (class1 instanceof PsiTypeParameter && class2 instanceof PsiTypeParameter) {
      if (!(Objects.equals(class1.getName(), class2.getName()) && ((PsiTypeParameter)class1).getIndex() == ((PsiTypeParameter)class2).getIndex())) return false;
      PsiClassType[] eTypes1 = class1.getExtendsListTypes();
      PsiClassType[] eTypes2 = class2.getExtendsListTypes();
      if (eTypes1.length != eTypes2.length) return false;
      for (int i = 0; i < eTypes1.length; i++) {
        PsiClassType eType1 = eTypes1[i];
        PsiClassType eType2 = eTypes2[i];
        if (visited.contains(eType1.getCanonicalText()) || visited.contains(eType2.getCanonicalText())) {
          return false;
        }
        if (!compareParamTypes(manager, eType1, eType2, visited)) return false;
      }
      return true;
    }

    return manager.areElementsEquivalent(class1, class2);
  }
}
