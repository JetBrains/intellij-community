// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import com.intellij.codeInsight.annoPackages.Jsr305Support;
import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
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
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.codeInsight.AnnotationUtil.NULLABLE;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker {
  private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

  private AnnotationPackageSupport[] myAnnotationSupports;

  private Map<String, AnnotationPackageSupport> myDefaultNullables;
  private Map<String, AnnotationPackageSupport> myDefaultNotNulls;
  private Map<String, AnnotationPackageSupport> myDefaultUnknowns;
  private List<String> myDefaultAll;
  public String myDefaultNullable = NULLABLE;
  public String myDefaultNotNull = NOT_NULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();
  private List<String> myInstrumentedNotNulls = ContainerUtil.newArrayList(NOT_NULL);
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  public NullableNotNullManagerImpl(Project project) {
    super(project);
    project.getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        updateDefaults();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        updateDefaults();
      }
    });
    updateDefaults();
    myNullables.addAll(myDefaultNullables.keySet());
    myNotNulls.addAll(myDefaultNotNulls.keySet());
  }

  private void updateDefaults() {
    myAnnotationSupports = AnnotationPackageSupport.EP_NAME.getExtensions();
    myDefaultNullables = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.NULLABLE).stream()).invert().toMap();
    myDefaultNotNulls = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.NOT_NULL).stream()).invert().toMap();
    myDefaultUnknowns = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.UNKNOWN).stream()).invert().toMap();
    myDefaultAll = StreamEx.of(myDefaultNullables, myDefaultNotNulls, myDefaultUnknowns).toFlatList(Map::keySet);
  }

  @Override
  public void setNotNulls(String @NotNull ... annotations) {
    myNotNulls.clear();
    Collections.addAll(myNotNulls, annotations);
    normalizeDefaults();
  }

  @Override
  public void setNullables(String @NotNull ... annotations) {
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
    return new ArrayList<>(myDefaultNullables.keySet());
  }

  @Override
  @NotNull
  List<String> getDefaultNotNulls() {
    return new ArrayList<>(myDefaultNotNulls.keySet());
  }

  @Override
  public @NotNull Optional<Nullability> getAnnotationNullability(String name) {
    if (myNotNulls.contains(name)) {
      return Optional.of(Nullability.NOT_NULL);
    }
    if (myNullables.contains(name)) {
      return Optional.of(Nullability.NULLABLE);
    }
    if (myDefaultUnknowns.containsKey(name)) {
      return Optional.of(Nullability.UNKNOWN);
    }
    return Optional.empty();
  }

  @Override
  public boolean isTypeUseAnnotationLocationRestricted(String name) {
    AnnotationPackageSupport support = findAnnotationSupport(name);
    return support != null && support.isTypeUseAnnotationLocationRestricted();
  }

  @Override
  public boolean canAnnotateLocals(String name) {
    AnnotationPackageSupport support = findAnnotationSupport(name);
    return support == null || support.canAnnotateLocals();
  }

  private @Nullable AnnotationPackageSupport findAnnotationSupport(String name) {
    AnnotationPackageSupport support = myDefaultUnknowns.get(name);
    if (support == null) {
      support = myDefaultNotNulls.get(name);
      if (support == null) {
        support = myDefaultNullables.get(name);
      }
    }
    return support;
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
           new HashSet<>(myNullables).equals(myDefaultNullables.keySet()) &&
           new HashSet<>(myNotNulls).equals(myDefaultNotNulls.keySet());
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
    myNotNulls.removeAll(myDefaultNullables.keySet());
    myNullables.removeAll(myDefaultNotNulls.keySet());
    myNullables.addAll(ContainerUtil.filter(myDefaultNullables.keySet(), s -> !myNullables.contains(s)));
    myNotNulls.addAll(ContainerUtil.filter(myDefaultNotNulls.keySet(), s -> !myNotNulls.contains(s)));
    myTracker.incModificationCount();
  }

  @NotNull
  private List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Set<PsiClass> result = new HashSet<>(getPossiblyUnresolvedJavaNicknameUsages());
      GlobalSearchScope scope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(myProject)) {
        @Override
        public boolean contains(@NotNull VirtualFile file) {
          return super.contains(file) && file.getFileType() != JavaFileType.INSTANCE;
        }
      };
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(Jsr305Support.TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), Jsr305Support::isNullabilityNickName));
      }
      return CachedValueProvider.Result.create(new ArrayList<>(result), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  // some frameworks use jsr305 annotations but don't have them in classpath
  @NotNull
  private List<PsiClass> getPossiblyUnresolvedJavaNicknameUsages() {
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
                                              PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                              @NotNull PsiElement context, boolean superPackage) {
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList == null) return null;
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (container instanceof PsiPackage) {
        VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
        VirtualFile ownerFile = PsiUtilCore.getVirtualFile(context);
        if (annotationFile != null && ownerFile != null && !annotationFile.equals(ownerFile)) {
          ProjectFileIndex index = ProjectRootManager.getInstance(container.getProject()).getFileIndex();
          VirtualFile annotationRoot = index.getClassRootForFile(annotationFile);
          VirtualFile ownerRoot = index.getClassRootForFile(ownerFile);
          if (ownerRoot != null && !ownerRoot.equals(annotationRoot)) {
            continue;
          }
        }
      }
      NullabilityAnnotationInfo result = checkNullityDefault(annotation, context, placeTargetTypes, superPackage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private NullabilityAnnotationInfo checkNullityDefault(@NotNull PsiAnnotation annotation,
                                                        @NotNull PsiElement context,
                                                        PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                        boolean superPackage) {
    for (AnnotationPackageSupport support : myAnnotationSupports) {
      NullabilityAnnotationInfo info = support.getNullabilityByContainerAnnotation(annotation, context, placeTargetTypes, superPackage);
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
      CachedValueProvider.Result.create(StreamEx.of(getNullables(), filterNickNames(Nullability.NULLABLE)).toFlatList(Function.identity()),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @NotNull
  @Override
  protected List<String> getNotNullsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(StreamEx.of(getNotNulls(), filterNickNames(Nullability.NOT_NULL)).toFlatList(Function.identity()),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @NotNull
  @Override
  protected NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Map<String, Nullability> result = new HashMap<>();
      for (String qName : myDefaultAll) {
        result.put(qName, null);
      }
      for (String qName : getNotNulls()) {
        result.put(qName, Nullability.NOT_NULL);
      }
      for (String qName : getNullables()) {
        result.put(qName, Nullability.NULLABLE);
      }
      for (String qName : myDefaultUnknowns.keySet()) {
        result.put(qName, Nullability.UNKNOWN);
      }
      for (PsiClass aClass : getAllNullabilityNickNames()) {
        String qName = aClass.getQualifiedName();
        if (qName != null) {
          result.putIfAbsent(qName, Jsr305Support.getNickNamedNullability(aClass));
        }
      }
      NullabilityAnnotationDataHolder holder = new NullabilityAnnotationDataHolder() {
        @Override
        public Set<String> qualifiedNames() {
          return result.keySet();
        }

        @Override
        public @Nullable Nullability getNullability(String annotation) {
          return result.get(annotation);
        }
      };
      return CachedValueProvider.Result.create(holder, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  protected @NotNull Nullability correctNullability(@NotNull Nullability nullability, @NotNull PsiAnnotation annotation) {
    if (nullability == Nullability.NOT_NULL && annotation.hasQualifiedName(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      Nullability correctedNullability = Jsr305Support.extractNullityFromWhenValue(annotation);
      if (correctedNullability != null) {
        return correctedNullability;
      }
    }
    return nullability;
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }
}
