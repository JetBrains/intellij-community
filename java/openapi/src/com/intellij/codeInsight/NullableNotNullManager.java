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
  public final JDOMExternalizableStringList myNullables = new JDOMExternalizableStringList();
  public final JDOMExternalizableStringList myNotNulls = new JDOMExternalizableStringList();

  public static final String[] DEFAULT_NULLABLES = {AnnotationUtil.NULLABLE, "javax.annotation.Nullable", "edu.umd.cs.findbugs.annotations.Nullable"};
  public static final String[] DEFAULT_NOT_NULLS = {AnnotationUtil.NOT_NULL, "javax.annotation.Nonnull",  "edu.umd.cs.findbugs.annotations.NonNull"};

  private static final Object LOCK = new Object();

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
    LOG.assertTrue(getNullables().contains(defaultNullable));
    myDefaultNullable = defaultNullable;
  }

  public String getDefaultNotNull() {
    return myDefaultNotNull;
  }

  public void setDefaultNotNull(@NotNull String defaultNotNull) {
    LOG.assertTrue(getNotNulls().contains(defaultNotNull));
    myDefaultNotNull = defaultNotNull;
  }

  public boolean isNullable(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNullables(), checkBases);
  }

  public boolean isNotNull(PsiModifierListOwner owner, boolean checkBases) {
    return AnnotationUtil.isAnnotated(owner, getNotNulls(), checkBases);
  }

  public List<String> getNullables() {
    if (myNullables.isEmpty()) {
      synchronized (LOCK) {
        if (myNullables.isEmpty()) {
          Collections.addAll(myNullables, DEFAULT_NULLABLES);
        }
      }
    }
    return myNullables;
  }

  public List<String> getNotNulls() {
    if (myNotNulls.isEmpty()) {
      synchronized (LOCK) {
        if (myNotNulls.isEmpty()) {
          Collections.addAll(myNotNulls, DEFAULT_NOT_NULLS);
        }
      }
    }
    return myNotNulls;
  }

  public boolean hasDefaultValues() {
    if (DEFAULT_NULLABLES.length != getNullables().size() || DEFAULT_NOT_NULLS.length != getNotNulls().size()) {
      return false;
    }
    if (myDefaultNotNull != AnnotationUtil.NOT_NULL || myDefaultNullable != AnnotationUtil.NULLABLE) {
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
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }
}
