// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import com.intellij.codeInsight.annoPackages.Jsr305Support;
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
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.codeInsight.AnnotationUtil.NULLABLE;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker {
  private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

  private final AnnotationPackageSupport[] myAnnotationSupports = AnnotationPackageSupport.getAnnotationPackages(this);

  private final List<String> myDefaultNullables =
    StreamEx.of(myAnnotationSupports).toFlatList(s -> s.getNullabilityAnnotations(Nullability.NULLABLE));
  private final List<String> myDefaultNotNulls =
    StreamEx.of(myAnnotationSupports).toFlatList(s -> s.getNullabilityAnnotations(Nullability.NOT_NULL));
  private final List<String> myDefaultAll = StreamEx.of(myAnnotationSupports)
    .flatCollection(s -> s.getNullabilityAnnotations(Nullability.UNKNOWN)).prepend(myDefaultNotNulls).prepend(myDefaultNullables).toList();
  public String myDefaultNullable = NULLABLE;
  public String myDefaultNotNull = NOT_NULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList(myDefaultNullables);
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList(myDefaultNotNulls);
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
  List<String> getDefaultNullables() {
    return myDefaultNullables;
  }

  @Override
  @NotNull
  List<String> getDefaultNotNulls() {
    return myDefaultNotNulls;
  }

  @Override
  @NotNull
  List<String> getAllDefaultAnnotations() {
    return myDefaultAll;
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
           new HashSet<>(myNullables).equals(new HashSet<>(getDefaultNullables())) &&
           new HashSet<>(myNotNulls).equals(new HashSet<>(getDefaultNotNulls()));
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
    myNotNulls.removeAll(getDefaultNullables());
    myNullables.removeAll(getDefaultNotNulls());
    myNullables.addAll(ContainerUtil.filter(getDefaultNullables(), s -> !myNullables.contains(s)));
    myNotNulls.addAll(ContainerUtil.filter(getDefaultNotNulls(), s -> !myNotNulls.contains(s)));
    myTracker.incModificationCount();
  }

  @NotNull
  private List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      List<PsiClass> result = new ArrayList<>();
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(Jsr305Support.TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), Jsr305Support::isNullabilityNickName));
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
    Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(StringUtil.getShortName(
      Jsr305Support.TYPE_QUALIFIER_NICKNAME), myProject, GlobalSearchScope.allScope(myProject));
    for (PsiAnnotation annotation : annotations) {
      PsiElement context = annotation.getContext();
      if (context instanceof PsiModifierList && context.getContext() instanceof PsiClass) {
        PsiClass ownerClass = (PsiClass)context.getContext();
        if (ownerClass.isAnnotationType() && Jsr305Support.isNullabilityNickName(ownerClass)) {
          result.add(ownerClass);
        }
      }
    }
    return result;
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
    for (AnnotationPackageSupport support : myAnnotationSupports) {
      NullabilityAnnotationInfo info = support.getNullabilityByContainerAnnotation(annotation, placeTargetTypes, superPackage);
      if (info != null) {
        return info;
      }
    }
    return null;
  }

  @NotNull
  private List<String> filterNickNames(@NotNull Nullability nullability) {
    return ContainerUtil.mapNotNull(getAllNullabilityNickNames(), c -> Jsr305Support.getNickNamedNullability(c) == nullability ? c.getQualifiedName() : null);
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
