// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.NOT_NULL;
import static com.intellij.codeInsight.AnnotationUtil.NULLABLE;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<NullableNotNullManagerImpl.StateBean> {
  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";
  private List<String> myNullables = ContainerUtil.newArrayList(DEFAULT_NULLABLES);
  private List<String> myNotNulls = ContainerUtil.newArrayList(DEFAULT_NOT_NULLS);

  public static class StateBean {
    @Tag("option") public Element myNullables = null;
    @Tag("option") public Element myNotNulls = null;
    @XCollection(style = XCollection.Style.v2) public List<String> instrumentedNotNulls = ContainerUtil.newArrayList(NOT_NULL);
    public String myDefaultNullable = NULLABLE;
    public String myDefaultNotNull = NOT_NULL;
  }

  private StateBean myState = new StateBean();

  public NullableNotNullManagerImpl(Project project) {
    super(project);
  }

  @Override
  public void setNotNulls(@NotNull String... annotations) {
    LinkedHashSet<String> set = ContainerUtil.newLinkedHashSet(annotations);
    Collections.addAll(set, DEFAULT_NOT_NULLS);
    set.removeAll(Arrays.asList(DEFAULT_NULLABLES));
    myNotNulls = new ArrayList<>(set);
  }

  @Override
  public void setNullables(@NotNull String... annotations) {
    LinkedHashSet<String> set = ContainerUtil.newLinkedHashSet(annotations);
    Collections.addAll(set, DEFAULT_NULLABLES);
    set.removeAll(Arrays.asList(DEFAULT_NOT_NULLS));
    myNullables = new ArrayList<>(set);
  }

  @Override
  @NotNull
  public String getDefaultNullable() {
    return myState.myDefaultNullable;
  }

  @Override
  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myState.myDefaultNullable = defaultNullable;
  }

  @Override
  @NotNull
  public String getDefaultNotNull() {
    return myState.myDefaultNotNull;
  }

  @Override
  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myState.myDefaultNotNull = defaultNotNull;
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
    return Collections.unmodifiableList(myState.instrumentedNotNulls);
  }

  @Override
  public void setInstrumentedNotNulls(@NotNull List<String> names) {
    myState.instrumentedNotNulls = ContainerUtil.sorted(names);
  }

  @Override
  protected boolean hasHardcodedContracts(PsiElement element) {
    return HardcodedContracts.hasHardcodedContracts(element);
  }


  @SuppressWarnings("deprecation")
  @Override
  public StateBean getState() {
    StateBean state = myState;

    state.myNullables = new Element("option").setAttribute("name", "myNullables").addContent(new Element("value"));
    new JDOMExternalizableStringList(myNullables).writeExternal(state.myNullables.getChild("value"));

    state.myNotNulls = new Element("option").setAttribute("name", "myNotNulls").addContent(new Element("value"));
    new JDOMExternalizableStringList(myNotNulls).writeExternal(state.myNotNulls.getChild("value"));

    return state;
  }

  @Override
  public void loadState(@NotNull StateBean state) {
    myState = state;

    readJdomList(state.myNullables, myNullables, DEFAULT_NULLABLES);
    readJdomList(state.myNotNulls, myNotNulls, DEFAULT_NOT_NULLS);
    myNullables.removeAll(Arrays.asList(DEFAULT_NOT_NULLS));
    myNotNulls.removeAll(Arrays.asList(DEFAULT_NULLABLES));
  }

  private static void readJdomList(@Nullable Element src, @NotNull List<String> to, @NotNull String[] defaults) {
    to.clear();
    Element value = src != null ? src.getChild("value") : null;
    if (value != null) {
      //noinspection deprecation
      JDOMExternalizableStringList.readList(to, value);
    }
    if (to.isEmpty()) {
      Collections.addAll(to, defaults);
    }
  }

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

  @Override
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

  private static Nullability getNickNamedNullability(@NotNull PsiClass psiClass) {
    if (AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null) return Nullability.UNKNOWN;

    PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
    return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullability.UNKNOWN;
  }

  @NotNull
  private static Nullability extractNullityFromWhenValue(PsiAnnotation nonNull) {
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

  private List<String> filterNickNames(Nullability nullability) {
    return StreamEx.of(getAllNullabilityNickNames()).filter(c -> getNickNamedNullability(c) == nullability).map(PsiClass::getQualifiedName).toList();
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
}
