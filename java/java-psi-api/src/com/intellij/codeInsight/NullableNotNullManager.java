/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifierListOwner;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 1/25/11
 */
public class NullableNotNullManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + NullableNotNullManager.class.getName());

  public String myDefaultNullable = AnnotationUtil.NULLABLE;
  public String myDefaultNotNull = AnnotationUtil.NOT_NULL;
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();

  public static final String[] DEFAULT_NULLABLES = {AnnotationUtil.NULLABLE, "javax.annotation.Nullable", "edu.umd.cs.findbugs.annotations.Nullable"};
  public static final String[] DEFAULT_NOT_NULLS = {AnnotationUtil.NOT_NULL, "javax.annotation.Nonnull",  "edu.umd.cs.findbugs.annotations.NonNull"};

  public NullableNotNullManager() {
    Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
    Collections.addAll(myNullables, DEFAULT_NULLABLES);
  }

  public static NullableNotNullManager getInstance(Project project) {
    return ServiceManager.getService(project, NullableNotNullManager.class);
  }

  public Collection<String> getAllAnnotations() {
    final List<String> all = new ArrayList<String>(getNullables());
    all.addAll(getNotNulls());
    return all;
  }

  private static void addAllIfNotPresent(Collection<String> collection, String... annotations) {
    for (String annotation : annotations) {
      LOG.assertTrue(annotation != null);
      if (!collection.contains(annotation)) {
        collection.add(annotation);
      }
    }
  }

  public void setNotNulls(String... annotations) {
    myNotNulls.clear();
    addAllIfNotPresent(myNotNulls, DEFAULT_NOT_NULLS);
    addAllIfNotPresent(myNotNulls, annotations);
  }

  public void setNullables(String... annotations) {
    myNullables.clear();
    addAllIfNotPresent(myNullables, DEFAULT_NULLABLES);
    addAllIfNotPresent(myNullables, annotations);
  }

  public String getDefaultNullable() {
    return myDefaultNullable;
  }
  
  public String getPresentableDefaultNullable() {
    return StringUtil.getShortName(myDefaultNullable);
  }
  
  @Nullable
  public String getNullable(PsiModifierListOwner owner) {
    for (String nullable : getNullables()) {
      if (AnnotationUtil.isAnnotated(owner, nullable, false, false)) return nullable;
    }
    return null;
  }

  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
  }

  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }
  public String getPresentableDefaultNotNull() {
    return StringUtil.getShortName(myDefaultNotNull);
  }
  
  @Nullable
  public String getNotNull(PsiModifierListOwner owner) {
    for (String notNull : getNotNulls()) {
      if (AnnotationUtil.isAnnotated(owner, notNull, false, false)) return notNull;
    }
    return null;
  }

  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
  }

  public boolean isNullable(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNullables(), checkBases, false);
  }

  public boolean isNotNull(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNotNulls(), checkBases, false);
  }

  public List<String> getNullables() {
    return myNullables;
  }

  public List<String> getNotNulls() {
    return myNotNulls;
  }

  public boolean hasDefaultValues() {
    if (DEFAULT_NULLABLES.length != getNullables().size() || DEFAULT_NOT_NULLS.length != getNotNulls().size()) {
      return false;
    }
    if (!myDefaultNotNull.equals(AnnotationUtil.NOT_NULL) || !myDefaultNullable.equals(AnnotationUtil.NULLABLE)) {
      return false;
    }
    for (int i = 0; i < DEFAULT_NULLABLES.length; i++) {
      if (!getNullables().get(i).equals(DEFAULT_NULLABLES[i])) {
        return false;
      }
    }
    for (int i = 0; i < DEFAULT_NOT_NULLS.length; i++) {
      if (!getNotNulls().get(i).equals(DEFAULT_NOT_NULLS[i])) {
        return false;
      }
    }

    return true;
  }

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

  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
      if (myNullables.isEmpty()) {
        Collections.addAll(myNullables, DEFAULT_NULLABLES);
      }
      if (myNotNulls.isEmpty()) {
        Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public static boolean isNullable(@NotNull PsiModifierListOwner owner) {
    return !isNotNull(owner) && getInstance(owner.getProject()).isNullable(owner, true);
  }

  public static boolean isNotNull(@NotNull PsiModifierListOwner owner) {
    return getInstance(owner.getProject()).isNotNull(owner, true);
  }
}
