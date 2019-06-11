// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.codeInsight.AnnotationUtil.NULLABLE;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker {
  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";
  private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

  public String myDefaultNullable = NULLABLE;
  public String myDefaultNotNull = NOT_NULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList(Arrays.asList(DEFAULT_NULLABLES));
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList(Arrays.asList(DEFAULT_NOT_NULLS));
  private List<String> myInstrumentedNotNulls = ContainerUtil.newArrayList(NOT_NULL);
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  public NullableNotNullManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void setNotNulls(@NotNull String... annotations) {
    myNotNulls.clear();
    Collections.addAll(myNotNulls, annotations);
    normalizeDefaults();
  }

  @Override
  public void setNullables(@NotNull String... annotations) {
    myNullables.clear();
    Collections.addAll(myNullables, annotations);
    normalizeDefaults();
  }

  @Override
  @NotNull
  public String getDefaultNullable() {
    return myDefaultNullable;
  }

  @Override
  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
    myTracker.incModificationCount();
  }

  @Override
  @NotNull
  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }

  @Override
  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
    myTracker.incModificationCount();
  }

  @Override
  @NotNull
  public List<String> getNullables() {
    return Collections.unmodifiableList(myNullables);
  }

  @Override
  @NotNull
  public List<String> getNotNulls() {
    return Collections.unmodifiableList(myNotNulls);
  }

  @NotNull
  @Override
  public List<String> getInstrumentedNotNulls() {
    return Collections.unmodifiableList(myInstrumentedNotNulls);
  }

  @Override
  public void setInstrumentedNotNulls(@NotNull List<String> names) {
    myInstrumentedNotNulls = ContainerUtil.sorted(names);
    myTracker.incModificationCount();
  }

  @Override
  protected boolean hasHardcodedContracts(@NotNull PsiElement element) {
    return HardcodedContracts.hasHardcodedContracts(element);
  }


  @Override
  public Element getState() {
    Element component = new Element("component");

    if (!hasDefaultValues()) {
      try {
        DefaultJDOMExternalizer.writeExternal(this, component);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }

    if (myInstrumentedNotNulls.size() != 1 || !NOT_NULL.equals(myInstrumentedNotNulls.get(0))) {
      // poor man's @XCollection(style = XCollection.Style.v2)
      Element instrumentedNotNulls = new Element(INSTRUMENTED_NOT_NULLS_TAG);
      for (String value : myInstrumentedNotNulls) {
        instrumentedNotNulls.addContent(new Element("option").setAttribute("value", value));
      }
      component.addContent(instrumentedNotNulls);
    }

    return component;
  }

  private boolean hasDefaultValues() {
    return NOT_NULL.equals(myDefaultNotNull) &&
           NULLABLE.equals(myDefaultNullable) &&
           new HashSet<>(myNullables).equals(ContainerUtil.newHashSet(DEFAULT_NULLABLES)) &&
           new HashSet<>(myNotNulls).equals(ContainerUtil.newHashSet(DEFAULT_NOT_NULLS));
  }

  @Override
  public void loadState(@NotNull Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      normalizeDefaults();
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }

    Element instrumented = state.getChild(INSTRUMENTED_NOT_NULLS_TAG);
    if (instrumented == null) {
      myInstrumentedNotNulls = ContainerUtil.newArrayList(NOT_NULL);
    }
    else {
      myInstrumentedNotNulls = ContainerUtil.mapNotNull(instrumented.getChildren("option"), o -> o.getAttributeValue("value"));
    }
  }

  private void normalizeDefaults() {
    myNotNulls.removeAll(ContainerUtil.newHashSet(DEFAULT_NULLABLES));
    myNullables.removeAll(ContainerUtil.newHashSet(DEFAULT_NOT_NULLS));
    myNullables.addAll(ContainerUtil.filter(DEFAULT_NULLABLES, s -> !myNullables.contains(s)));
    myNotNulls.addAll(ContainerUtil.filter(DEFAULT_NOT_NULLS, s -> !myNotNulls.contains(s)));
    myTracker.incModificationCount();
  }

  @NotNull
  private List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      List<PsiClass> result = new ArrayList<>();
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), NullableNotNullManagerImpl::isNullabilityNickName));
      }
      if (nickDeclarations.length == 0) {
        result.addAll(getUnresolvedNicknameUsages());
      }
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  // some frameworks use jsr305 annotations but don't have them in classpath
  @NotNull
  private List<PsiClass> getUnresolvedNicknameUsages() {
    List<PsiClass> result = new ArrayList<>();
    Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(StringUtil.getShortName(TYPE_QUALIFIER_NICKNAME), myProject, GlobalSearchScope.allScope(myProject));
    for (PsiAnnotation annotation : annotations) {
      PsiElement context = annotation.getContext();
      if (context instanceof PsiModifierList && context.getContext() instanceof PsiClass) {
        PsiClass ownerClass = (PsiClass)context.getContext();
        if (ownerClass.isAnnotationType() && isNullabilityNickName(ownerClass)) {
          result.add(ownerClass);
        }
      }
    }
    return result;
  }

  @Nullable
  protected NullabilityAnnotationInfo isJsr305Default(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType[] placeTargetTypes) {
    PsiClass declaration = resolveAnnotationType(annotation);
    PsiModifierList modList = declaration == null ? null : declaration.getModifierList();
    if (modList == null) return null;

    PsiAnnotation tqDefault = AnnotationUtil.findAnnotation(declaration, true, "javax.annotation.meta.TypeQualifierDefault");
    if (tqDefault == null) return null;

    Set<PsiAnnotation.TargetType> required = AnnotationTargetUtil.extractRequiredAnnotationTargets(tqDefault.findAttributeValue(null));
    if (required == null || (!required.isEmpty() && !ContainerUtil.intersects(required, Arrays.asList(placeTargetTypes)))) return null;
    
    for (PsiAnnotation qualifier : modList.getAnnotations()) {
      Nullability nullability = getJsr305QualifierNullability(qualifier);
      if (nullability != null) {
        return new NullabilityAnnotationInfo(annotation, nullability, true);
      }
    }
    return null;
  }

  @Override
  @Nullable
  NullabilityAnnotationInfo getNullityDefault(@NotNull PsiModifierListOwner container,
                                              @NotNull PsiAnnotation.TargetType[] placeTargetTypes,
                                              PsiModifierListOwner owner, boolean superPackage) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) return null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (container instanceof PsiPackage) {
        VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
        VirtualFile ownerFile = PsiUtilCore.getVirtualFile(owner);
        if (annotationFile != null && ownerFile != null && !annotationFile.equals(ownerFile)) {
          ProjectFileIndex index = ProjectRootManager.getInstance(container.getProject()).getFileIndex();
          VirtualFile annotationRoot = index.getClassRootForFile(annotationFile);
          VirtualFile ownerRoot = index.getClassRootForFile(ownerFile);
          if (ownerRoot != null && !ownerRoot.equals(annotationRoot)) {
            continue;
          }
        }
      }
      NullabilityAnnotationInfo result = checkNullityDefault(annotation, placeTargetTypes, superPackage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private NullabilityAnnotationInfo checkNullityDefault(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType[] placeTargetTypes, boolean superPackage) {
    NullabilityAnnotationInfo jsr = superPackage ? null : isJsr305Default(annotation, placeTargetTypes);
    return jsr != null ? jsr : CheckerFrameworkNullityUtil.isCheckerDefault(annotation, placeTargetTypes);
  }

  @Nullable
  private static PsiClass resolveAnnotationType(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
    PsiElement declaration = element == null ? null : element.resolve();
    if (!(declaration instanceof PsiClass) || !((PsiClass)declaration).isAnnotationType()) return null;
    return (PsiClass)declaration;
  }

  @Nullable
  private Nullability getJsr305QualifierNullability(@NotNull PsiAnnotation qualifier) {
    String qName = qualifier.getQualifiedName();
    if (qName == null || !qName.startsWith("javax.annotation.")) return null;

    if (qName.equals(JAVAX_ANNOTATION_NULLABLE) && getNullables().contains(qName)) return Nullability.NULLABLE;
    if (qName.equals(JAVAX_ANNOTATION_NONNULL)) return extractNullityFromWhenValue(qualifier);
    return null;
  }

  private static boolean isNullabilityNickName(@NotNull PsiClass candidate) {
    String qname = candidate.getQualifiedName();
    if (qname == null || qname.startsWith("javax.annotation.")) return false;
    return getNickNamedNullability(candidate) != Nullability.UNKNOWN;
  }

  @NotNull
  private static Nullability getNickNamedNullability(@NotNull PsiClass psiClass) {
    if (AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null) return Nullability.UNKNOWN;

    PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
    return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullability.UNKNOWN;
  }

  @NotNull
  private static Nullability extractNullityFromWhenValue(@NotNull PsiAnnotation nonNull) {
    PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
    if (when instanceof PsiReferenceExpression) {
      String refName = ((PsiReferenceExpression)when).getReferenceName();
      if ("ALWAYS".equals(refName)) {
        return Nullability.NOT_NULL;
      }
      if ("MAYBE".equals(refName) || "NEVER".equals(refName)) {
        return Nullability.NULLABLE;
      }
    }
    return Nullability.UNKNOWN;
  }

  @NotNull
  private List<String> filterNickNames(@NotNull Nullability nullability) {
    return ContainerUtil.mapNotNull(getAllNullabilityNickNames(), c -> getNickNamedNullability(c) == nullability ? c.getQualifiedName() : null);
  }

  @NotNull
  @Override
  protected List<String> getNullablesWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> 
      CachedValueProvider.Result.create(ContainerUtil.concat(getNullables(), filterNickNames(Nullability.NULLABLE)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @NotNull
  @Override
  protected List<String> getNotNullsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(ContainerUtil.concat(getNotNulls(), filterNickNames(Nullability.NOT_NULL)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @NotNull
  @Override
  protected Set<String> getAllNullabilityAnnotationsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Set<String> result = new THashSet<>();
      result.addAll(getNotNulls());
      result.addAll(getNullables());
      result.addAll(ContainerUtil.mapNotNull(getAllNullabilityNickNames(), PsiClass::getQualifiedName));
      return CachedValueProvider.Result.create(Collections.unmodifiableSet(result), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }
}
