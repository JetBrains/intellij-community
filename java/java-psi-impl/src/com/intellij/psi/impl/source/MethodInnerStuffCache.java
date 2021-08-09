// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class MethodInnerStuffCache {
  private final PsiExtensibleMethod myMethod;
  private final Ref<Pair<Long, Interner<PsiElement>>> myInterner = Ref.create();

  public MethodInnerStuffCache(@NotNull PsiExtensibleMethod myMethod) {
    this.myMethod = myMethod;
  }

  @NotNull
  public PsiParameterList getParametersList(){
    return CachedValuesManager.getProjectPsiDependentCache(myMethod, __ -> calcParameters());
  }

  @NotNull
  public PsiTypeParameterList getTypeParametersList(){
    return CachedValuesManager.getProjectPsiDependentCache(myMethod, __ -> calcTypeParameters());
  }

  @NotNull
  public PsiReferenceList getThrowsList(){
    return CachedValuesManager.getProjectPsiDependentCache(myMethod, __ -> calcThrows());
  }

  @Nullable
  public PsiCodeBlock getBody(){
    return CachedValuesManager.getProjectPsiDependentCache(myMethod, __ -> calcBody());
  }

  private @NotNull PsiParameterList calcParameters() {
    LightParameterListBuilder builder = new LightParameterListBuilder(myMethod.getManager(), JavaLanguage.INSTANCE);
    Arrays.stream(myMethod.getOwnParametersList().getParameters())
      .forEach(builder::addParameter);
    internMembers(PsiAugmentProvider.collectAugments(myMethod, PsiParameterList.class, null))
      .stream()
      .map(PsiParameterList::getParameters)
      .flatMap(Arrays::stream)
      .forEach(builder::addParameter);
    return builder;
  }

  private @NotNull PsiTypeParameterList calcTypeParameters() {
    LightTypeParameterListBuilder builder = new LightTypeParameterListBuilder(myMethod.getManager(), JavaLanguage.INSTANCE);
    Arrays.stream(myMethod.getOwnTypeParametersList().getTypeParameters())
      .forEach(builder::addParameter);
    internMembers(PsiAugmentProvider.collectAugments(myMethod, PsiTypeParameterList.class, null))
      .stream()
      .map(PsiTypeParameterList::getTypeParameters)
      .flatMap(Arrays::stream)
      .forEach(builder::addParameter);
    return builder;
  }

  private @NotNull PsiReferenceList calcThrows() {
    LightReferenceListBuilder builder = new LightReferenceListBuilder(myMethod.getManager(), JavaLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST);
    Arrays.stream(myMethod.getOwnThrowsList().getReferencedTypes())
      .forEach(builder::addReference);
    internMembers(PsiAugmentProvider.collectAugments(myMethod, PsiReferenceList.class, null))
      .stream()
      .peek(this::checkReferenceList)
      .map(PsiReferenceList::getReferencedTypes)
      .flatMap(Arrays::stream)
      .forEach(builder::addReference);
    return builder;
  }

  private @Nullable PsiCodeBlock calcBody() {
    PsiCodeBlock body = myMethod.getOwnBody();
    if(body == null){
      return null;
    }

    PsiCodeBlock result = (PsiCodeBlock) body.copy();
    internMembers(PsiAugmentProvider.collectAugments(myMethod, PsiStatement.class, null))
      .forEach(statement -> result.addBefore(statement, result.getFirstBodyElement()));
    return result;
  }

  private void checkReferenceList(PsiReferenceList list){
    if (list.getRole() == PsiReferenceList.Role.THROWS_LIST) {
      return;
    }

    throw new IllegalArgumentException("Invalid augment for PsiExtensibleMethod: expected PsiReferenceList with role THROWS_LIST, got " + list.getRole().name());
  }


  @NotNull
  private <T extends PsiElement> List<T> internMembers(List<T> members) {
    return ContainerUtil.map(members, this::internMember);
  }

  private <T extends PsiElement> T internMember(T m) {
    if (m == null) return null;
    long modCount = myMethod.getManager().getModificationTracker().getModificationCount();
    synchronized (myInterner) {
      Pair<Long, Interner<PsiElement>> pair = myInterner.get();
      if (pair == null || pair.first.longValue() != modCount) {
        myInterner.set(pair = Pair.create(modCount, Interner.createWeakInterner()));
      }
      //noinspection unchecked
      return (T)pair.second.intern(m);
    }
  }
}