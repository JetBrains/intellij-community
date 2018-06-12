// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInspection.dataFlow.HardcodedContracts;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerNotNullableSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@State(name = "NullableNotNullManager")
public class NullableNotNullManagerImpl extends NullableNotNullManager implements PersistentStateComponent<Element> {

  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";

  public NullableNotNullManagerImpl(Project project) {
    super(project);
    myNotNulls.addAll(getPredefinedNotNulls());
  }

  @Override
  public List<String> getPredefinedNotNulls() {
    return JpsJavaCompilerNotNullableSerializer.DEFAULT_NOT_NULLS;
  }

  @Override
  protected boolean hasHardcodedContracts(PsiElement element) {
    return HardcodedContracts.hasHardcodedContracts(element);
  }


  @SuppressWarnings("deprecation")
  @Override
  public Element getState() {
    final Element component = new Element("component");

    if (hasDefaultValues()) {
      return component;
    }

    try {
      DefaultJDOMExternalizer.writeExternal(this, component);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return component;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void loadState(@NotNull Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      if (myNullables.isEmpty()) {
        Collections.addAll(myNullables, DEFAULT_NULLABLES);
      }
      if (myNotNulls.isEmpty()) {
        myNotNulls.addAll(getPredefinedNotNulls());
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
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

  private static boolean isNullabilityNickName(@NotNull PsiClass candidate) {
    String qname = candidate.getQualifiedName();
    if (qname == null || qname.startsWith("javax.annotation.")) return false;
    return getNickNamedNullability(candidate) != Nullness.UNKNOWN;
  }

  private static Nullness getNickNamedNullability(@NotNull PsiClass psiClass) {
    if (AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null) return Nullness.UNKNOWN;

    PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
    return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullness.UNKNOWN;
  }

  @NotNull
  private static Nullness extractNullityFromWhenValue(PsiAnnotation nonNull) {
    PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
    if (when instanceof PsiReferenceExpression) {
      String refName = ((PsiReferenceExpression)when).getReferenceName();
      if ("ALWAYS".equals(refName)) {
        return Nullness.NOT_NULL;
      }
      if ("MAYBE".equals(refName) || "NEVER".equals(refName)) {
        return Nullness.NULLABLE;
      }
    }
    return Nullness.UNKNOWN;
  }

  private List<String> filterNickNames(Nullness nullness) {
    return StreamEx.of(getAllNullabilityNickNames()).filter(c -> getNickNamedNullability(c) == nullness).map(PsiClass::getQualifiedName).toList();
  }

  @NotNull
  @Override
  protected List<String> getNullablesWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> 
      CachedValueProvider.Result.create(ContainerUtil.concat(getNullables(), filterNickNames(Nullness.NULLABLE)), 
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  @NotNull
  @Override
  protected List<String> getNotNullsWithNickNames() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () ->
      CachedValueProvider.Result.create(ContainerUtil.concat(getNotNulls(), filterNickNames(Nullness.NOT_NULL)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }
}
