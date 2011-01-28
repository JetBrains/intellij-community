/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiModifierListOwner;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 1/25/11
 */
@State(
    name = "NullableNotNullManager",
    storages = {@Storage(id = "default", file = "$PROJECT_FILE$")}
)
public class NullableNotNullManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + NullableNotNullManager.class.getName());

  public String myDefaultNullable = AnnotationUtil.NULLABLE;

  public String myDefaultNotNull = AnnotationUtil.NOT_NULL;
  public JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();

  public static final String[] DEFAULT_NULLABLES = {AnnotationUtil.NULLABLE, "javax.annotation.Nullable", "edu.umd.cs.findbugs.annotations.Nullable"};
  public static final String[] DEFAULT_NOT_NULLS = {AnnotationUtil.NOT_NULL, "javax.annotation.Nonnull",  "edu.umd.cs.findbugs.annotations.NonNull"};

  public static NullableNotNullManager getInstance(Project project) {
    return ServiceManager.getService(project, NullableNotNullManager.class);
  }

  public Collection<String> getAllAnnotations() {
    final List<String> all = new ArrayList<String>(getNullables());
    all.addAll(getNotNulls());
    return all;
  }

  public void setNotNulls(Object[] anns) {
    myNotNulls.clear();
    for (Object ann : anns) {
      myNotNulls.add((String)ann);
    }
  }

  public void setNullables(Object[] anns) {
    myNullables.clear();
    for (Object ann : anns) {
      myNullables.add((String)ann);
    }
  }

  public String getDefaultNullable() {
    return myDefaultNullable;
  }

  public void setDefaultNullable(@NotNull String defaultNullable) {
    LOG.assertTrue(myNullables.contains(defaultNullable));
    myDefaultNullable = defaultNullable;
  }

  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }

  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(myNotNulls.contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
  }

  public List<String> getNullables() {
    if (myNullables.isEmpty()) {
      Collections.addAll(myNullables, DEFAULT_NULLABLES);
    }
    return myNullables;
  }

  public boolean isNullable(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNullables(), checkBases);
  }

  public boolean isNotNull(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNotNulls(), checkBases);
  }

  public List<String> getNotNulls() {
    if (myNotNulls.isEmpty()) {
      Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
    }
    return myNotNulls;
  }

  @Override
  public Element getState() {
    final Element component = new Element("component");

    if (getNullables().size() == DEFAULT_NULLABLES.length && getNotNulls().size() == DEFAULT_NOT_NULLS.length) {
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
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }
}
