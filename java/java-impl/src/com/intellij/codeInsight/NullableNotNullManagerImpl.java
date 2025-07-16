// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import com.intellij.codeInsight.annoPackages.Jsr305Support;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.codeInsight.AnnotationUtil.NULLABLE;
import static com.intellij.codeInspection.options.OptPane.*;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element>, ModificationTracker,
                                                                                  Disposable {
  private static final String INSTRUMENTED_NOT_NULLS_TAG = "instrumentedNotNulls";

  private List<AnnotationPackageSupport> myAnnotationSupports;

  private Map<String, AnnotationPackageSupport> myDefaultNullables;
  private Map<String, AnnotationPackageSupport> myDefaultNotNulls;
  private Map<String, AnnotationPackageSupport> myDefaultUnknowns;
  private List<String> myDefaultAll;
  public String myDefaultNullable = NULLABLE;
  public String myDefaultNotNull = NOT_NULL;
  public boolean myOrdered = false;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();
  private List<String> myInstrumentedNotNulls = List.of(NOT_NULL);
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  public NullableNotNullManagerImpl(Project project) {
    super(project);
    AnnotationPackageSupport.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(AnnotationPackageSupport extension, @NotNull PluginDescriptor pluginDescriptor) {
        updateDefaults();
      }

      @Override
      public void extensionRemoved(AnnotationPackageSupport extension, @NotNull PluginDescriptor pluginDescriptor) {
        updateDefaults();
      }
    }, this);
    updateDefaults();
  }

  private void updateDefaults() {
    myAnnotationSupports = AnnotationPackageSupport.EP_NAME.getExtensionList();
    myDefaultNullables = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.NULLABLE).stream()).invert().toCustomMap(LinkedHashMap::new);
    myDefaultNotNulls = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.NOT_NULL).stream()).invert().toCustomMap(LinkedHashMap::new);
    myDefaultUnknowns = StreamEx.of(myAnnotationSupports)
      .cross(s -> s.getNullabilityAnnotations(Nullability.UNKNOWN).stream()).invert().toCustomMap(LinkedHashMap::new);
    myDefaultAll = StreamEx.of(myDefaultNullables, myDefaultNotNulls, myDefaultUnknowns).toFlatList(Map::keySet);
    normalizeDefaults();
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
  public @NotNull String getDefaultAnnotation(@NotNull Nullability nullability, @NotNull PsiElement context) {
    Collection<String> annotations = switch(nullability) {
      case NOT_NULL -> myNotNulls;
      case NULLABLE -> myNullables;
      case UNKNOWN -> myDefaultUnknowns.keySet();
    };
    PsiFile containingFile = context.getContainingFile();
    if (containingFile instanceof DummyHolder) {
      PsiElement element = containingFile.getContext();
      if (element != null) {
        containingFile = element.getContainingFile();
      }
    }
    PsiFile file = containingFile.getOriginalFile();
    Module module = ModuleUtilCore.findModuleForFile(file);
    if (module == null) return getDefaultAnnotation(nullability);
    for (String annotation : annotations) {
      if (JavaLibraryUtil.hasLibraryClass(module, annotation)) {
        return annotation;
      }
    }
    return getDefaultAnnotation(nullability);
  }

  private @NotNull String getDefaultAnnotation(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> getDefaultNotNull();
      case NULLABLE -> getDefaultNullable();
      case UNKNOWN -> AnnotationUtil.UNKNOWN_NULLABILITY;
    };
  }

  @Override
  public @NotNull String getDefaultNullable() {
    return myDefaultNullable;
  }

  @Override
  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
    myTracker.incModificationCount();
  }

  @Override
  public @NotNull String getDefaultNotNull() {
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
  public List<String> getDefaultNullables() {
    return new ArrayList<>(myDefaultNullables.keySet());
  }

  @Override
  @NotNull
  public List<String> getDefaultNotNulls() {
    return new ArrayList<>(myDefaultNotNulls.keySet());
  }

  @Override
  public @NotNull Optional<Nullability> getAnnotationNullability(String name) {
    return Optional.ofNullable(getAllNullabilityAnnotationsWithNickNames().getNullability(name));
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
  public @NotNull List<String> getNullables() {
    return Collections.unmodifiableList(myNullables);
  }

  @Override
  public @NotNull List<String> getNotNulls() {
    return Collections.unmodifiableList(myNotNulls);
  }

  @Override
  public @NotNull List<String> getInstrumentedNotNulls() {
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
           myNullables.equals(new ArrayList<>(myDefaultNullables.keySet())) &&
           myNotNulls.equals(new ArrayList<>(myDefaultNotNulls.keySet()));
  }
  
  @Override
  public void loadState(@NotNull Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      normalizeDefaults();
      migrateSettings();
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }

    Element instrumented = state.getChild(INSTRUMENTED_NOT_NULLS_TAG);
    if (instrumented == null) {
      myInstrumentedNotNulls = List.of(NOT_NULL);
    }
    else {
      myInstrumentedNotNulls = ContainerUtil.mapNotNull(instrumented.getChildren("option"), o -> o.getAttributeValue("value"));
    }
  }

  /**
   * Convert old settings when the order of annotations didn't matter:
   * 1. Put the default annotation on top, unless it's JetBrains default annotation
   * 2. Next, put all the custom annotations
   * 3. Next, put all annotations from {@link AnnotationPackageSupport} extensions in the order they are provided
   */
  private void migrateSettings() {
    if (!myOrdered) {
      myOrdered = true;
      // Set the default order and put default to the front
      myNullables.removeAll(myDefaultNullables.keySet());
      myNullables.addAll(myDefaultNullables.keySet());
      if (!myDefaultNullable.equals(NULLABLE)) {
        myNullables.remove(myDefaultNullable);
        myNullables.add(0, myDefaultNullable);
      }
      myNotNulls.removeAll(myDefaultNotNulls.keySet());
      myNotNulls.addAll(myDefaultNotNulls.keySet());
      if (!myDefaultNotNull.equals(NOT_NULL)) {
        myNotNulls.remove(myDefaultNotNull);
        myNotNulls.add(0, myDefaultNotNull);
      }
    }
  }

  private void normalizeDefaults() {
    myNotNulls.removeAll(myDefaultNullables.keySet());
    myNullables.removeAll(myDefaultNotNulls.keySet());
    myNullables.addAll(ContainerUtil.filter(myDefaultNullables.keySet(), s -> !myNullables.contains(s)));
    myNotNulls.addAll(ContainerUtil.filter(myDefaultNotNulls.keySet(), s -> !myNotNulls.contains(s)));
    myTracker.incModificationCount();
  }

  private @NotNull List<PsiClass> getAllNullabilityNickNames() {
    if (!getNotNulls().contains(Jsr305Support.JAVAX_ANNOTATION_NONNULL)) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Set<PsiClass> result = new HashSet<>(getPossiblyUnresolvedJavaNicknameUsages());
      GlobalSearchScope scope = new DelegatingGlobalSearchScope(GlobalSearchScope.allScope(myProject)) {
        @Override
        public boolean contains(@NotNull VirtualFile file) {
          return super.contains(file) && !FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE);
        }
      };
      PsiClass[] nickDeclarations = JavaPsiFacade.getInstance(myProject).findClasses(Jsr305Support.TYPE_QUALIFIER_NICKNAME, scope);
      for (PsiClass tqNick : nickDeclarations) {
        result.addAll(ContainerUtil.findAll(MetaAnnotationUtil.getChildren(tqNick, scope), Jsr305Support::isNullabilityNickName));
      }
      return Result.create(new ArrayList<>(result), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  // some frameworks use jsr305 annotations but don't have them in classpath
  private @NotNull List<PsiClass> getPossiblyUnresolvedJavaNicknameUsages() {
    List<PsiClass> result = new ArrayList<>();
    Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().getAnnotations(StringUtil.getShortName(
      Jsr305Support.TYPE_QUALIFIER_NICKNAME), myProject, GlobalSearchScope.allScope(myProject));
    for (PsiAnnotation annotation : annotations) {
      PsiElement context = annotation.getContext();
      if (context instanceof PsiModifierList && context.getContext() instanceof PsiClass ownerClass &&
          ownerClass.isAnnotationType() && Jsr305Support.isNullabilityNickName(ownerClass)) {
        result.add(ownerClass);
      }
    }
    return result;
  }

  @Override
  protected @NotNull ContextNullabilityInfo findNullityDefaultOnPackage(PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                        PsiFile file) {
    boolean superPackage = false;
    ContextNullabilityInfo info = ContextNullabilityInfo.EMPTY;
    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return info;
    VirtualFile root = index.getSourceRootForFile(vFile);
    boolean compiled = false;
    if (root == null) {
      root = index.getClassRootForFile(vFile);
      if (root == null) return info;
      compiled = true;
    }
    // Single-file source root -- no package-info processing for now
    if (root.equals(vFile)) return info;
    PsiDirectory directory = file.getContainingDirectory();
    while (directory != null) {
      PsiFile packageFile = directory.findFile(compiled ? PsiPackage.PACKAGE_INFO_CLS_FILE : PsiPackage.PACKAGE_INFO_FILE);
      if (packageFile instanceof PsiJavaFile javaFile) {
        PsiPackageStatement stmt = javaFile.getPackageStatement();
        if (stmt != null) {
          PsiModifierList modifierList = stmt.getAnnotationList();
          if (modifierList != null) {
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
              info = info.orElse(checkNullityDefault(annotation, placeTargetTypes, superPackage));
            }
          }
        }
      }
      if (root.equals(directory.getVirtualFile())) break;
      directory = directory.getParentDirectory();
      superPackage = true;
    }
    return info;
  }

  @Override
  protected @NotNull ContextNullabilityInfo getNullityDefault(@NotNull PsiModifierListOwner container,
                                                              PsiAnnotation.TargetType @NotNull [] placeTargetTypes) {
    LOG.assertTrue(!(container instanceof PsiPackage)); // Packages are handled separately in findNullityDefaultOnPackage
    ContextNullabilityInfo res = ContextNullabilityInfo.EMPTY;
    PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        ContextNullabilityInfo info = checkNullityDefault(annotation, placeTargetTypes, false);
        res = res.orElse(info);
      }
    }
    return res;
  }

  private @NotNull ContextNullabilityInfo checkNullityDefault(@NotNull PsiAnnotation annotation,
                                                              PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                              boolean superPackage) {
    ContextNullabilityInfo info = ContextNullabilityInfo.EMPTY;
    for (AnnotationPackageSupport support : myAnnotationSupports) {
      info = info.orElse(support.getNullabilityByContainerAnnotation(annotation, placeTargetTypes, superPackage));
    }
    return info;
  }

  private @Unmodifiable @NotNull List<String> filterNickNames(@NotNull Nullability nullability) {
    return ContainerUtil.mapNotNull(getAllNullabilityNickNames(), c -> Jsr305Support.getNickNamedNullability(c) == nullability ? c.getQualifiedName() : null);
  }

  @Override
  @NotNull
  public List<String> getNullablesWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      Result.create(StreamEx.of(getNullables(), filterNickNames(Nullability.NULLABLE)).toFlatList(Function.identity()),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  @NotNull
  public List<String> getNotNullsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      Result.create(StreamEx.of(getNotNulls(), filterNickNames(Nullability.NOT_NULL)).toFlatList(Function.identity()),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  protected @NotNull NullabilityAnnotationDataHolder getAllNullabilityAnnotationsWithNickNames() {
    if (DumbService.isDumb(myProject) || myProject.isDefault()) {
      // Searching for nullability nicknames is not available in the dumb mode or for default project
      return NullabilityAnnotationDataHolder.fromMap(getNullabilityMap());
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> {
      Map<String, Nullability> result = getNullabilityMap();
      for (PsiClass aClass : getAllNullabilityNickNames()) {
        String qName = aClass.getQualifiedName();
        if (qName != null) {
          result.putIfAbsent(qName, Jsr305Support.getNickNamedNullability(aClass));
        }
      }
      NullabilityAnnotationDataHolder holder = NullabilityAnnotationDataHolder.fromMap(result);
      return Result.create(holder, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private @NotNull Map<String, Nullability> getNullabilityMap() {
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
    return result;
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

  @Override
  protected @NotNull ContextNullabilityInfo findNullityDefaultOnModule(PsiAnnotation.@NotNull TargetType @NotNull [] targetTypes,
                                                                       @NotNull PsiElement element) {
    PsiJavaModule module = JavaPsiModuleUtil.findDescriptorByElement(element);
    if (module != null) {
      return getNullityDefault(module, targetTypes);
    }
    return ContextNullabilityInfo.EMPTY;
  }
  
  public @NotNull OptionController getOptionController() {
    return OptionController.fieldsOf(this)
      .withRootPane(() -> pane(
        tabs(
          tab(NullableNotNullDialog.NULLABLE,
              string("myDefaultNullable", JavaBundle.message("nullable.notnull.annotation.used.label")),
              stringList("myNullables", JavaBundle.message("nullable.notnull.annotations.panel.title", "Nullable"),
                         new JavaClassValidator().annotationsOnly())),
          tab(NullableNotNullDialog.NOT_NULL,
              string("myDefaultNotNull", JavaBundle.message("nullable.notnull.annotation.used.label")),
              stringList("myNotNulls", JavaBundle.message("nullable.notnull.annotations.panel.title", "NotNull"),
                         new JavaClassValidator().annotationsOnly())
          ))));
  }

  @Override
  public final @Nullable NullabilityAnnotationInfo findEffectiveNullabilityInfo(@NotNull PsiModifierListOwner owner) {
    PsiType type = PsiUtil.getTypeByPsiElement(owner);
    if (type == null || TypeConversionUtil.isPrimitiveAndNotNull(type)) return null;

    return CachedValuesManager.getCachedValue(owner, () -> {
      NullabilityAnnotationInfo info = doFindEffectiveNullabilityAnnotation(owner);

      PsiFile file = owner.getContainingFile();
      if (file != null
          && file.getVirtualFile() != null
          && ProjectFileIndex.getInstance(owner.getProject()).isInLibrary(file.getVirtualFile())) {
        // there is no need to recompute info on changes in the project code
        return Result.create(info, JavaLibraryModificationTracker.getInstance(owner.getProject()));
      }

      return Result.create(info, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public void dispose() {
    
  }

  /**
   * Provides options to setup nullability annotations:
   * <ul>
   *   <li>NullableNotNullManager.myNullables - list of nullable annotation fqns</li>
   *   <li>NullableNotNullManager.myNotNulls - list of notnull annotation fqns</li>
   *   <li>NullableNotNullManager.myDefaultNullable - default nullable annotation fqn</li>
   *   <li>NullableNotNullManager.myDefaultNotNull - default notnull annotation fqn</li>
   * </ul>
   */
  public static final class Provider implements OptionControllerProvider {
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      Project project = context.getProject();
      return ((NullableNotNullManagerImpl)getInstance(project)).getOptionController()
        .onValueSet((bindId, value) -> ProjectInspectionProfileManager.getInstance(project).fireProfileChanged());
    }

    @Override
    public @NotNull String name() {
      return "NullableNotNullManager";
    }
  }
}
