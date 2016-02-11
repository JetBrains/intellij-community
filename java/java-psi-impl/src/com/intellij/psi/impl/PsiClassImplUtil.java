/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.ClassInnerStuffCache;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.*;
import com.intellij.psi.util.*;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.RowIcon;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author ik
 * @since 24.10.2003
 */
public class PsiClassImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiClassImplUtil");
  private static final Key<ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass>> MAP_IN_CLASS_KEY = Key.create("MAP_KEY");
  private static final String VALUES_METHOD = "values";
  private static final String VALUE_OF_METHOD = "valueOf";

  private PsiClassImplUtil() { }

  @NotNull
  public static PsiField[] getAllFields(@NotNull PsiClass aClass) {
    List<PsiField> map = getAllByMap(aClass, MemberType.FIELD);
    return map.toArray(new PsiField[map.size()]);
  }

  @NotNull
  public static PsiMethod[] getAllMethods(@NotNull PsiClass aClass) {
    List<PsiMethod> methods = getAllByMap(aClass, MemberType.METHOD);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull
  public static PsiClass[] getAllInnerClasses(@NotNull PsiClass aClass) {
    List<PsiClass> classes = getAllByMap(aClass, MemberType.CLASS);
    return classes.toArray(new PsiClass[classes.size()]);
  }

  @Nullable
  public static PsiField findFieldByName(@NotNull PsiClass aClass, String name, boolean checkBases) {
    List<PsiMember> byMap = findByMap(aClass, name, checkBases, MemberType.FIELD);
    return byMap.isEmpty() ? null : (PsiField)byMap.get(0);
  }

  @NotNull
  public static PsiMethod[] findMethodsByName(@NotNull PsiClass aClass, String name, boolean checkBases) {
    List<PsiMember> methods = findByMap(aClass, name, checkBases, MemberType.METHOD);
    //noinspection SuspiciousToArrayCall
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod patternMethod, final boolean checkBases) {
    final List<PsiMethod> result = findMethodsBySignature(aClass, patternMethod, checkBases, true);
    return result.isEmpty() ? null : result.get(0);
  }

  // ----------------------------- findMethodsBySignature -----------------------------------

  @NotNull
  public static PsiMethod[] findMethodsBySignature(@NotNull PsiClass aClass, @NotNull PsiMethod patternMethod, final boolean checkBases) {
    List<PsiMethod> methods = findMethodsBySignature(aClass, patternMethod, checkBases, false);
    return methods.toArray(new PsiMethod[methods.size()]);
  }

  @NotNull
  private static List<PsiMethod> findMethodsBySignature(@NotNull PsiClass aClass,
                                                        @NotNull PsiMethod patternMethod,
                                                        boolean checkBases,
                                                        boolean stopOnFirst) {
    final PsiMethod[] methodsByName = aClass.findMethodsByName(patternMethod.getName(), checkBases);
    if (methodsByName.length == 0) return Collections.emptyList();
    final List<PsiMethod> methods = new SmartList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (final PsiMethod method : methodsByName) {
      final PsiClass superClass = method.getContainingClass();
      final PsiSubstitutor substitutor;
      if (checkBases && !aClass.equals(superClass) && superClass != null) {
        substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
      final MethodSignature signature = method.getSignature(substitutor);
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

  @NotNull
  private static List<PsiMember> findByMap(@NotNull PsiClass aClass, String name, boolean checkBases, @NotNull MemberType type) {
    if (name == null) return Collections.emptyList();

    if (checkBases) {
      Map<String, List<Pair<PsiMember, PsiSubstitutor>>> allMethodsMap = getMap(aClass, type);
      List<Pair<PsiMember, PsiSubstitutor>> list = allMethodsMap.get(name);
      if (list == null) return Collections.emptyList();
      List<PsiMember> ret = new ArrayList<PsiMember>(list.size());
      for (final Pair<PsiMember, PsiSubstitutor> info : list) {
        ret.add(info.getFirst());
      }

      return ret;
    }
    else {
      PsiMember[] members = null;
      switch (type) {
        case METHOD:
          members = aClass.getMethods();
          break;
        case CLASS:
          members = aClass.getInnerClasses();
          break;
        case FIELD:
          members = aClass.getFields();
          break;
      }

      List<PsiMember> list = new ArrayList<PsiMember>();
      for (PsiMember member : members) {
        if (name.equals(member.getName())) {
          list.add(member);
        }
      }
      return list;
    }
  }

  @NotNull
  public static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> getAllWithSubstitutorsByMap(@NotNull PsiClass aClass, @NotNull MemberType type) {
    Map<String, List<Pair<PsiMember, PsiSubstitutor>>> allMap = getMap(aClass, type);
    //noinspection unchecked
    return (List)allMap.get(ALL);
  }

  @NotNull
  private static <T extends PsiMember> List<T> getAllByMap(@NotNull PsiClass aClass, @NotNull MemberType type) {
    List<Pair<T, PsiSubstitutor>> pairs = getAllWithSubstitutorsByMap(aClass, type);

    final List<T> ret = new ArrayList<T>(pairs.size());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < pairs.size(); i++) {
      Pair<T, PsiSubstitutor> pair = pairs.get(i);
      T t = pair.getFirst();
      LOG.assertTrue(t != null, aClass);
      ret.add(t);
    }
    return ret;
  }

  @NonNls private static final String ALL = "Intellij-IDEA-ALL";

  public enum MemberType {CLASS, FIELD, METHOD}

  private static Map<String, List<Pair<PsiMember, PsiSubstitutor>>> getMap(@NotNull PsiClass aClass, @NotNull MemberType type) {
    ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> value = getValues(aClass);
    return value.getValue(aClass).get(aClass.getResolveScope()).get(type);
  }

  @NotNull
  private static ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> getValues(@NotNull PsiClass aClass) {
    ParameterizedCachedValue<Map<GlobalSearchScope, MembersMap>, PsiClass> value = aClass.getUserData(MAP_IN_CLASS_KEY);
    if (value == null) {
      value = CachedValuesManager.getManager(aClass.getProject()).createParameterizedCachedValue(ByNameCachedValueProvider.INSTANCE, false);
      //Do not cache for nonphysical elements
      if (aClass.isPhysical()) {
        value = ((UserDataHolderEx)aClass).putUserDataIfAbsent(MAP_IN_CLASS_KEY, value);
      }
    }
    return value;
  }

  private static class ClassIconRequest {
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

  private static final Function<ClassIconRequest, Icon> FULL_ICON_EVALUATOR = new NullableFunction<ClassIconRequest, Icon>() {
    @Override
    public Icon fun(ClassIconRequest r) {
      if (!r.psiClass.isValid() || r.psiClass.getProject().isDisposed()) return null;

      final boolean isLocked = (r.flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !r.psiClass.isWritable();
      Icon symbolIcon = r.symbolIcon != null
                        ? r.symbolIcon
                        : ElementPresentationUtil.getClassIconOfKind(r.psiClass, ElementPresentationUtil.getClassKind(r.psiClass));
      RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(symbolIcon, r.psiClass, isLocked);
      Icon result = ElementPresentationUtil.addVisibilityIcon(r.psiClass, r.flags, baseIcon);
      Iconable.LastComputedIcon.put(r.psiClass, result, r.flags);
      return result;
    }
  };

  public static Icon getClassIcon(final int flags, @NotNull PsiClass aClass) {
    return getClassIcon(flags, aClass, null);
  }

  public static Icon getClassIcon(int flags, @NotNull PsiClass aClass, @Nullable Icon symbolIcon) {
    Icon base = Iconable.LastComputedIcon.get(aClass, flags);
    if (base == null) {
      if (symbolIcon == null) {
        symbolIcon = ElementPresentationUtil.getClassIconOfKind(aClass, ElementPresentationUtil.getBasicClassKind(aClass));
      }
      RowIcon baseIcon = ElementBase.createLayeredIcon(aClass, symbolIcon, 0);
      base = ElementPresentationUtil.addVisibilityIcon(aClass, flags, baseIcon);
    }

    return IconDeferrer.getInstance().defer(base, new ClassIconRequest(aClass, flags, symbolIcon), FULL_ICON_EVALUATOR);
  }

  @NotNull
  public static SearchScope getClassUseScope(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return new LocalSearchScope(aClass);
    }
    final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(aClass);
    PsiFile file = aClass.getContainingFile();
    if (PsiImplUtil.isInServerPage(file)) return maximalUseScope;
    final PsiClass containingClass = aClass.getContainingClass();
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
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    String name = method.getName();
    if (!("main".equals(name) || "premain".equals(name) || !"agentmain".equals(name))) return false;

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
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

  private static class MembersMap extends ConcurrentFactoryMap<MemberType, Map<String, List<Pair<PsiMember, PsiSubstitutor>>>> {
    private final PsiClass myPsiClass;
    private final GlobalSearchScope myResolveScope;

    public MembersMap(PsiClass psiClass, GlobalSearchScope scope) {
      myPsiClass = psiClass;
      myResolveScope = scope;
    }

    @Nullable
    @Override
    protected Map<String, List<Pair<PsiMember, PsiSubstitutor>>> create(final MemberType key) {
      final Map<String, List<Pair<PsiMember, PsiSubstitutor>>> map = new THashMap<String, List<Pair<PsiMember, PsiSubstitutor>>>();

      final List<Pair<PsiMember, PsiSubstitutor>> allMembers = new ArrayList<Pair<PsiMember, PsiSubstitutor>>();
      map.put(ALL, allMembers);

      ElementClassFilter filter = key == MemberType.CLASS ? ElementClassFilter.CLASS :
                                  key == MemberType.METHOD ? ElementClassFilter.METHOD :
                                  ElementClassFilter.FIELD;
      final ElementClassHint classHint = new ElementClassHint() {
        @Override
        public boolean shouldProcess(DeclarationKind kind) {
          return key == MemberType.CLASS && kind == DeclarationKind.CLASS ||
                 key == MemberType.FIELD && (kind == DeclarationKind.FIELD || kind == DeclarationKind.ENUM_CONST) ||
                 key == MemberType.METHOD && kind == DeclarationKind.METHOD;
        }
      };
      FilterScopeProcessor<MethodCandidateInfo> processor = new FilterScopeProcessor<MethodCandidateInfo>(filter) {
        @Override
        protected void add(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
          if (key == MemberType.CLASS && element instanceof PsiClass ||
              key == MemberType.METHOD && element instanceof PsiMethod ||
              key == MemberType.FIELD && element instanceof PsiField) {
            Pair<PsiMember, PsiSubstitutor> info = Pair.create((PsiMember)element, substitutor);
            allMembers.add(info);
            String currentName = ((PsiMember)element).getName();
            List<Pair<PsiMember, PsiSubstitutor>> listByName = map.get(currentName);
            if (listByName == null) {
              listByName = new ArrayList<Pair<PsiMember, PsiSubstitutor>>(1);
              map.put(currentName, listByName);
            }
            listByName.add(info);
          }
        }

        @Override
        public <K> K getHint(@NotNull Key<K> hintKey) {
          //noinspection unchecked
          return ElementClassHint.KEY == hintKey ? (K)classHint : super.getHint(hintKey);
        }
      };

      processDeclarationsInClassNotCached(myPsiClass, processor, ResolveState.initial(), null, null, myPsiClass, false,
                                          PsiUtil.getLanguageLevel(myPsiClass), myResolveScope);
      return map;
    }
  }

  private static class ByNameCachedValueProvider implements ParameterizedCachedValueProvider<Map<GlobalSearchScope, MembersMap>, PsiClass> {
    private static final ByNameCachedValueProvider INSTANCE = new ByNameCachedValueProvider();

    @Override
    public CachedValueProvider.Result<Map<GlobalSearchScope, MembersMap>> compute(@NotNull final PsiClass myClass) {
      final Map<GlobalSearchScope, MembersMap> map = new ConcurrentFactoryMap<GlobalSearchScope, MembersMap>() {
        @Nullable
        @Override
        protected MembersMap create(GlobalSearchScope resolveScope) {
          return new MembersMap(myClass, resolveScope);
        }
      };
      return CachedValueProvider.Result.create(map, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
  }

  public static boolean processDeclarationsInEnum(@NotNull PsiScopeProcessor processor,
                                                  @NotNull ResolveState state,
                                                  @NotNull ClassInnerStuffCache innerStuffCache) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      if ((nameHint == null || VALUES_METHOD.equals(nameHint.getName(state)))) {
        PsiMethod method = innerStuffCache.getValuesMethod();
        if (method != null && !processor.execute(method, ResolveState.initial())) return false;
      }
      if ((nameHint == null || VALUE_OF_METHOD.equals(nameHint.getName(state)))) {
        PsiMethod method = innerStuffCache.getValueOfMethod();
        if (method != null && !processor.execute(method, ResolveState.initial())) return false;
      }
    }

    return true;
  }

  public static boolean processDeclarationsInClass(@NotNull PsiClass aClass,
                                                   @NotNull final PsiScopeProcessor processor,
                                                   @NotNull ResolveState state,
                                                   @Nullable Set<PsiClass> visited,
                                                   PsiElement last,
                                                   @NotNull PsiElement place,
                                                   @NotNull LanguageLevel languageLevel,
                                                   boolean isRaw) {
    return processDeclarationsInClass(aClass, processor, state, visited, last, place, languageLevel, isRaw, place.getResolveScope());
  }

  private static boolean processDeclarationsInClass(@NotNull PsiClass aClass,
                                                    @NotNull final PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    @Nullable Set<PsiClass> visited,
                                                    PsiElement last,
                                                    @NotNull PsiElement place,
                                                    @NotNull LanguageLevel languageLevel,
                                                    boolean isRaw,
                                                    @NotNull GlobalSearchScope resolveScope) {
    if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList) {
      return true; //TypeParameterList and ModifierList do not see our declarations
    }
    if (visited != null && visited.contains(aClass)) return true;

    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    isRaw = isRaw || PsiUtil.isRawSubstitutor(aClass, substitutor);

    final NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint != null) {
      String name = nameHint.getName(state);
      return processCachedMembersByName(aClass, processor, state, visited, last, place, isRaw, substitutor,
                                        getValues(aClass).getValue(aClass).get(resolveScope), name,languageLevel);
    }
    return processDeclarationsInClassNotCached(aClass, processor, state, visited, last, place, isRaw, languageLevel, resolveScope);
  }

  private static boolean processCachedMembersByName(@NotNull PsiClass aClass,
                                                    @NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    @Nullable Set<PsiClass> visited,
                                                    PsiElement last,
                                                    @NotNull PsiElement place,
                                                    boolean isRaw,
                                                    @NotNull PsiSubstitutor substitutor,
                                                    @NotNull MembersMap value,
                                                    String name,
                                                    @NotNull LanguageLevel languageLevel) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      final PsiField fieldByName = aClass.findFieldByName(name, false);
      if (fieldByName != null) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
        if (!processor.execute(fieldByName, state)) return false;
      }
      else {
        final Map<String, List<Pair<PsiMember, PsiSubstitutor>>> allFieldsMap = value.get(MemberType.FIELD);

        final List<Pair<PsiMember, PsiSubstitutor>> list = allFieldsMap.get(name);
        if (list != null) {
          boolean resolved = false;
          for (final Pair<PsiMember, PsiSubstitutor> candidate : list) {
            PsiMember candidateField = candidate.getFirst();
            PsiClass containingClass = candidateField.getContainingClass();
            if (containingClass == null) {
              LOG.error("No class for field " + candidateField.getName() + " of " + candidateField.getClass());
              continue;
            }
            PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                     substitutor, factory, languageLevel);

            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
            if (!processor.execute(candidateField, state.put(PsiSubstitutor.KEY, finalSubstitutor))) {
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
        final PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, state, last, place)) return false;
      }
      if (!(last instanceof PsiReferenceList)) {
        final PsiClass classByName = aClass.findInnerClassByName(name, false);
        if (classByName != null) {
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          if (!processor.execute(classByName, state)) return false;
        }
        else {
          Map<String, List<Pair<PsiMember, PsiSubstitutor>>> allClassesMap = value.get(MemberType.CLASS);

          List<Pair<PsiMember, PsiSubstitutor>> list = allClassesMap.get(name);
          if (list != null) {
            boolean resolved = false;
            for (final Pair<PsiMember, PsiSubstitutor> candidate : list) {
              PsiMember inner = candidate.getFirst();
              PsiClass containingClass = inner.getContainingClass();
              if (containingClass != null) {
                PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                         substitutor, factory, languageLevel);
                processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
                if (!processor.execute(inner, state.put(PsiSubstitutor.KEY, finalSubstitutor))) {
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
        final MethodResolverProcessor methodResolverProcessor = (MethodResolverProcessor)processor;
        if (methodResolverProcessor.isConstructor()) {
          final PsiMethod[] constructors = aClass.getConstructors();
          methodResolverProcessor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
          for (PsiMethod constructor : constructors) {
            if (!methodResolverProcessor.execute(constructor, state)) return false;
          }
          return true;
        }
      }
      Map<String, List<Pair<PsiMember, PsiSubstitutor>>> allMethodsMap = value.get(MemberType.METHOD);
      List<Pair<PsiMember, PsiSubstitutor>> list = allMethodsMap.get(name);
      if (list != null) {
        boolean resolved = false;
        for (final Pair<PsiMember, PsiSubstitutor> candidate : list) {
          ProgressIndicatorProvider.checkCanceled();
          PsiMethod candidateMethod = (PsiMethod)candidate.getFirst();
          if (processor instanceof MethodResolverProcessor) {
            if (candidateMethod.isConstructor() != ((MethodResolverProcessor)processor).isConstructor()) continue;
          }
          final PsiClass containingClass = candidateMethod.getContainingClass();
          if (containingClass == null || visited != null && visited.contains(containingClass)) {
            continue;
          }

          PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(containingClass, candidate.getSecond(), aClass,
                                                                   substitutor, factory, languageLevel);
          finalSubstitutor = checkRaw(isRaw, factory, candidateMethod, finalSubstitutor);
          processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
          if (!processor.execute(candidateMethod, state.put(PsiSubstitutor.KEY, finalSubstitutor))) {
            resolved = true;
          }
        }
        if (resolved) return false;

        if (visited != null) {
          for (Pair<PsiMember, PsiSubstitutor> aList : list) {
            visited.add(aList.getFirst().getContainingClass());
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
      final PsiClass containingClass = candidateMethod.getContainingClass();
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
    final PsiType containingType = elementFactory.createType(candidateClass, candidateSubstitutor, languageLevel);
    PsiType type = substitutor.substitute(containingType);
    if (!(type instanceof PsiClassType)) return candidateSubstitutor;
    return ((PsiClassType)type).resolveGenerics().getSubstitutor();
  }

  private static boolean processDeclarationsInClassNotCached(@NotNull PsiClass aClass,
                                                             @NotNull PsiScopeProcessor processor,
                                                             @NotNull ResolveState state,
                                                             @Nullable Set<PsiClass> visited,
                                                             PsiElement last,
                                                             @NotNull PsiElement place,
                                                             boolean isRaw,
                                                             @NotNull LanguageLevel languageLevel,
                                                             @NotNull GlobalSearchScope resolveScope) {
    if (visited == null) visited = new THashSet<PsiClass>();
    if (!visited.add(aClass)) return true;
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, aClass);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    final NameHint nameHint = processor.getHint(NameHint.KEY);


    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)) {
      if (nameHint != null) {
        final PsiField fieldByName = aClass.findFieldByName(nameHint.getName(state), false);
        if (fieldByName != null && !processor.execute(fieldByName, state)) return false;
      }
      else {
        final PsiField[] fields = aClass.getFields();
        for (final PsiField field : fields) {
          if (!processor.execute(field, state)) return false;
        }
      }
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
      PsiSubstitutor baseSubstitutor = state.get(PsiSubstitutor.KEY);
      final PsiMethod[] methods = nameHint != null ? aClass.findMethodsByName(nameHint.getName(state), false) : aClass.getMethods();
      for (final PsiMethod method : methods) {
        PsiSubstitutor finalSubstitutor = checkRaw(isRaw, factory, method, baseSubstitutor);
        ResolveState methodState = finalSubstitutor == baseSubstitutor ? state : state.put(PsiSubstitutor.KEY, finalSubstitutor);
        if (!processor.execute(method, methodState)) return false;
      }
    }

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      if (last != null && last.getContext() == aClass) {
        // Parameters
        final PsiTypeParameterList list = aClass.getTypeParameterList();
        if (list != null && !list.processDeclarations(processor, ResolveState.initial(), last, place)) return false;
      }

      if (!(last instanceof PsiReferenceList) && !(last instanceof PsiModifierList)) {
        // Inners
        if (nameHint != null) {
          final PsiClass inner = aClass.findInnerClassByName(nameHint.getName(state), false);
          if (inner != null) {
            if (!processor.execute(inner, state)) return false;
          }
        }
        else {
          final PsiClass[] inners = aClass.getInnerClasses();
          for (final PsiClass inner : inners) {
            if (!processor.execute(inner, state)) return false;
          }
        }
      }
    }

    return last instanceof PsiReferenceList || processSuperTypes(aClass, processor, visited, last, place, state, isRaw, factory,
                                                                 languageLevel, resolveScope);
  }

  @Nullable
  public static <T extends PsiType> T correctType(@Nullable final T originalType, @NotNull final GlobalSearchScope resolveScope) {
    if (originalType == null || !Registry.is("java.correct.class.type.by.place.resolve.scope")) {
      return originalType;
    }

    return new TypeCorrector(resolveScope).correctType(originalType);
  }

  public static List<PsiClassType.ClassResolveResult> getScopeCorrectedSuperTypes(final PsiClass aClass, GlobalSearchScope resolveScope) {
    Map<GlobalSearchScope, List<PsiClassType.ClassResolveResult>> cache =
      CachedValuesManager.getCachedValue(aClass, new CachedValueProvider<Map<GlobalSearchScope, List<PsiClassType.ClassResolveResult>>>() {
        @Nullable
        @Override
        public Result<Map<GlobalSearchScope, List<PsiClassType.ClassResolveResult>>> compute() {
          Map<GlobalSearchScope, List<PsiClassType.ClassResolveResult>> map = new ConcurrentFactoryMap<GlobalSearchScope, List<PsiClassType.ClassResolveResult>>() {
            @NotNull
            @Override
            protected List<PsiClassType.ClassResolveResult> create(final GlobalSearchScope resolveScope) {
              return calcScopeCorrectedSuperTypes(resolveScope, aClass);
            }
          };
          return Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
      });
    return cache.get(resolveScope);
  }

  @NotNull
  private static List<PsiClassType.ClassResolveResult> calcScopeCorrectedSuperTypes(GlobalSearchScope resolveScope, PsiClass aClass) {
    List<PsiClassType.ClassResolveResult> answer = ContainerUtil.newArrayList();
    for (PsiClassType type : aClass.getSuperTypes()) {
      PsiClassType corrected = correctType(type, resolveScope);
      if (corrected == null) continue;

      PsiClassType.ClassResolveResult result = ((PsiClassType)PsiUtil.captureToplevelWildcards(corrected, aClass)).resolveGenerics();
      PsiClass superClass = result.getElement();
      if (superClass == null || !PsiSearchScopeUtil.isInScope(resolveScope, superClass)) continue;

      answer.add(result);
    }
    return answer;
  }

  private static boolean processSuperTypes(@NotNull PsiClass aClass,
                                           @NotNull PsiScopeProcessor processor,
                                           @Nullable Set<PsiClass> visited,
                                           PsiElement last,
                                           @NotNull PsiElement place,
                                           @NotNull ResolveState state,
                                           boolean isRaw,
                                           @NotNull PsiElementFactory factory,
                                           @NotNull LanguageLevel languageLevel, GlobalSearchScope resolveScope) {
    boolean resolved = false;
    for (PsiClassType.ClassResolveResult superTypeResolveResult : getScopeCorrectedSuperTypes(aClass, resolveScope)) {
      PsiClass superClass = superTypeResolveResult.getElement();
      assert superClass != null;
      PsiSubstitutor finalSubstitutor = obtainFinalSubstitutor(superClass, superTypeResolveResult.getSubstitutor(), aClass,
                                                               state.get(PsiSubstitutor.KEY), factory, languageLevel);
      if (!processDeclarationsInClass(superClass, processor, state.put(PsiSubstitutor.KEY, finalSubstitutor), visited, last, place,
                                      languageLevel, isRaw, resolveScope)) {
        resolved = true;
      }
    }
    return !resolved;
  }

  @Nullable
  public static PsiClass getSuperClass(@NotNull PsiClass psiClass) {

    if (psiClass.isInterface()) {
      String className = CommonClassNames.JAVA_LANG_OBJECT;
      return findSpecialSuperClass(psiClass, className);
    }
    if (psiClass.isEnum()) {
      return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_ENUM);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      if (baseClass == null || baseClass.isInterface()) return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
      return baseClass;
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return null;

    final PsiClassType[] referenceElements = psiClass.getExtendsListTypes();

    if (referenceElements.length == 0) return findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);

    PsiClass psiResolved = referenceElements[0].resolve();
    return psiResolved == null ? findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT) : psiResolved;
  }

  @Nullable
  private static PsiClass findSpecialSuperClass(@NotNull PsiClass psiClass, String className) {
    return JavaPsiFacade.getInstance(psiClass.getProject()).findClass(className, psiClass.getResolveScope());
  }

  @NotNull
  public static PsiClass[] getSupers(@NotNull PsiClass psiClass) {
    final PsiClass[] supers = getSupersInner(psiClass);
    for (final PsiClass aSuper : supers) {
      LOG.assertTrue(aSuper != null);
    }
    return supers;
  }

  @NotNull
  private static PsiClass[] getSupersInner(@NotNull PsiClass psiClass) {
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
        final PsiClass objectClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_OBJECT);
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

  @NotNull
  public static PsiClassType[] getSuperTypes(@NotNull PsiClass psiClass) {
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
    return factory.createTypeByFQClassName("java.lang.annotation.Annotation", psiClass.getResolveScope());
  }

  private static PsiClassType getEnumSuperType(@NotNull PsiClass psiClass, @NotNull PsiElementFactory factory) {
    PsiClassType superType;
    final PsiClass enumClass = findSpecialSuperClass(psiClass, CommonClassNames.JAVA_LANG_ENUM);
    if (enumClass == null) {
      try {
        superType = (PsiClassType)factory.createTypeFromText(CommonClassNames.JAVA_LANG_ENUM, null);
      }
      catch (IncorrectOperationException e) {
        superType = null;
      }
    }
    else {
      final PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], factory.createType(psiClass));
      }
      superType = new PsiImmediateClassType(enumClass, substitutor);
    }
    return superType;
  }

  @NotNull
  public static PsiClass[] getInterfaces(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType[] referencedTypes = typeParameter.getExtendsListTypes();
    if (referencedTypes.length == 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    final List<PsiClass> result = new ArrayList<PsiClass>(referencedTypes.length);
    for (PsiClassType referencedType : referencedTypes) {
      final PsiClass psiClass = referencedType.resolve();
      if (psiClass != null && psiClass.isInterface()) {
        result.add(psiClass);
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  public static PsiClass[] getInterfaces(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) {
      return resolveClassReferenceList(psiClass.getExtendsListTypes(), psiClass, false);
    }

    if (psiClass instanceof PsiAnonymousClass) {
      PsiClassType baseClassReference = ((PsiAnonymousClass)psiClass).getBaseClassType();
      PsiClass baseClass = baseClassReference.resolve();
      return baseClass != null && baseClass.isInterface() ? new PsiClass[]{baseClass} : PsiClass.EMPTY_ARRAY;
    }

    final PsiClassType[] implementsListTypes = psiClass.getImplementsListTypes();
    return resolveClassReferenceList(implementsListTypes, psiClass, false);
  }

  @NotNull
  private static PsiClass[] resolveClassReferenceList(@NotNull PsiClassType[] listOfTypes,
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
      PsiClass[] shorter = new PsiClass[resolvedCount];
      System.arraycopy(resolved, 0, shorter, 0, resolvedCount);
      resolved = shorter;
    }

    return resolved;
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull PsiClass psiClass,
                                                                                            String name,
                                                                                            boolean checkBases) {
    if (!checkBases) {
      final PsiMethod[] methodsByName = psiClass.findMethodsByName(name, false);
      final List<Pair<PsiMethod, PsiSubstitutor>> ret = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>(methodsByName.length);
      for (final PsiMethod method : methodsByName) {
        ret.add(Pair.create(method, PsiSubstitutor.EMPTY));
      }
      return ret;
    }
    Map<String, List<Pair<PsiMember, PsiSubstitutor>>> map = getMap(psiClass, MemberType.METHOD);
    @SuppressWarnings("unchecked")
    List<Pair<PsiMethod, PsiSubstitutor>> list = (List)map.get(name);
    return list == null ?
           Collections.<Pair<PsiMethod, PsiSubstitutor>>emptyList() :
           Collections.unmodifiableList(list);
  }

  @NotNull
  public static PsiClassType[] getExtendsListTypes(@NotNull PsiClass psiClass) {
    if (psiClass.isEnum()) {
      PsiClassType enumSuperType = getEnumSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory());
      return enumSuperType == null ? PsiClassType.EMPTY_ARRAY : new PsiClassType[]{enumSuperType};
    }
    if (psiClass.isAnnotationType()) {
      return new PsiClassType[]{getAnnotationSuperType(psiClass, JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory())};
    }
    PsiType upperBound = psiClass.getUserData(InferenceSession.UPPER_BOUND);
    if (upperBound == null && psiClass instanceof PsiTypeParameter) {
      upperBound = LambdaUtil.getFunctionalTypeMap().get(psiClass);
    }
    if (upperBound instanceof PsiIntersectionType) {
      final PsiType[] conjuncts = ((PsiIntersectionType)upperBound).getConjuncts();
      final List<PsiClassType> result = new ArrayList<PsiClassType>();
      for (PsiType conjunct : conjuncts) {
        if (conjunct instanceof PsiClassType) {
          result.add((PsiClassType)conjunct);
        }
      }
      return result.toArray(new PsiClassType[result.size()]);
    }
    else if (upperBound instanceof PsiClassType) {
      return new PsiClassType[] {(PsiClassType)upperBound};
    }
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  public static PsiClassType[] getImplementsListTypes(@NotNull PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getImplementsList();
    if (extendsList != null) {
      return extendsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
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

        return p1.getIndex() == p2.getIndex() &&
               (aClass.getManager().areElementsEquivalent(p1.getOwner(), p2.getOwner()) || TypeConversionUtil.areSameFreshVariables(p1, p2));
      }
      else {
        return false;
      }
    }
    if (qName1.hashCode() != qName2.hashCode() || !qName1.equals(qName2)) {
      return false;
    }

    if (aClass.getOriginalElement().equals(another.getOriginalElement())) {
      return true;
    }

    final PsiFile file1 = aClass.getContainingFile().getOriginalFile();
    final PsiFile file2 = another.getContainingFile().getOriginalFile();

    //see com.intellij.openapi.vcs.changes.PsiChangeTracker
    //see com.intellij.psi.impl.PsiFileFactoryImpl#createFileFromText(CharSequence,PsiFile)
    final PsiFile original1 = file1.getUserData(PsiFileFactory.ORIGINAL_FILE);
    final PsiFile original2 = file2.getUserData(PsiFileFactory.ORIGINAL_FILE);
    if (original1 == original2 && original1 != null || original1 == file2 || original2 == file1 || file1 == file2) {
      return compareClassSeqNumber(aClass, (PsiClass)another);
    }

    final FileIndexFacade fileIndex = ServiceManager.getService(file1.getProject(), FileIndexFacade.class);
    final VirtualFile vfile1 = file1.getViewProvider().getVirtualFile();
    final VirtualFile vfile2 = file2.getViewProvider().getVirtualFile();
    boolean lib1 = fileIndex.isInLibraryClasses(vfile1);
    boolean lib2 = fileIndex.isInLibraryClasses(vfile2);

    return (fileIndex.isInSource(vfile1) || lib1) && (fileIndex.isInSource(vfile2) || lib2);
  }

  private static boolean compareClassSeqNumber(@NotNull PsiClass aClass, @NotNull PsiClass another) {
    // there may be several classes in one file, they must not be equal
    int index1 = getSeqNumber(aClass);
    if (index1 == -1) return true;
    int index2 = getSeqNumber(another);
    return index1 == index2;
  }

  private static int getSeqNumber(@NotNull PsiClass aClass) {
    // sequence number of this class among its parent' child classes named the same
    PsiElement parent = aClass.getParent();
    if (parent == null) return -1;
    int seqNo = 0;
    for (PsiElement child : parent.getChildren()) {
      if (child == aClass) return seqNo;
      if (child instanceof PsiClass && Comparing.strEqual(aClass.getName(), ((PsiClass)child).getName())) {
        seqNo++;
      }
    }
    return -1;
  }

  public static boolean isFieldEquivalentTo(@NotNull PsiField field, PsiElement another) {
    if (!(another instanceof PsiField)) return false;
    String name1 = field.getName();
    if (name1 == null) return false;
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
      if (!compareParamTypes(manager, type1, type2, new HashSet<String>())) return false;
    }
    return true;
  }

  private static boolean compareParamTypes(@NotNull PsiManager manager, @NotNull PsiType type1, @NotNull PsiType type2, Set<String> visited) {
    if (type1 instanceof PsiArrayType) {
      if (type2 instanceof PsiArrayType) {
        final PsiType componentType1 = ((PsiArrayType)type1).getComponentType();
        final PsiType componentType2 = ((PsiArrayType)type2).getComponentType();
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
      if (!(Comparing.equal(class1.getName(), class2.getName()) && ((PsiTypeParameter)class1).getIndex() == ((PsiTypeParameter)class2).getIndex())) return false;
      final PsiClassType[] eTypes1 = class1.getExtendsListTypes();
      final PsiClassType[] eTypes2 = class2.getExtendsListTypes();
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
