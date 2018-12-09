/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SmartRefElementPointerImpl implements SmartRefElementPointer {
  @NonNls public static final String FQNAME_ATTR = "FQNAME";
  @NonNls public static final String TYPE_ATTR = "TYPE";
  @NonNls public static final String ENTRY_POINT = "entry_point";
  private static final Logger LOG = Logger.getInstance(SmartRefElementPointerImpl.class);

  private final boolean myIsPersistent;
  private RefEntity myRefElement;
  private final String myFQName;
  private final String myType;

  public SmartRefElementPointerImpl(RefEntity ref, boolean isPersistent) {
    myIsPersistent = isPersistent;
    myRefElement = ref;
    myFQName = ref.getExternalName();
    myType = ref.getRefManager().getType(ref);
    if (myFQName == null) {
      boolean psiExists = ref instanceof RefElement && ((RefElement)ref).getPsiElement() != null;
      LOG.error("Name: " + ref.getName() +
                ", qName: " + ref.getQualifiedName() +
                "; type: " + myType +
                "; psi exists: " + psiExists +
                (ref instanceof RefElement ? ("; containing file: " + getContainingFileName((RefElement)ref)) : ""));
    }
  }


  public SmartRefElementPointerImpl(Element jDomElement) {
    myIsPersistent = true;
    myRefElement = null;
    myFQName = jDomElement.getAttributeValue(FQNAME_ATTR);
    myType = jDomElement.getAttributeValue(TYPE_ATTR);
 }

  public SmartRefElementPointerImpl(final String type, final String fqName) {
     myIsPersistent = true;
     myFQName = fqName;
     myType = type;
   }

  @Override
  public boolean isPersistent() {
    return myIsPersistent;
  }

  @Override
  public String getFQName() {
    return myFQName;
  }

  @Override
  public RefEntity getRefElement() {
    return myRefElement;
  }

  @Override
  public void writeExternal(Element parentNode) {
    Element element = new Element(ENTRY_POINT);
    element.setAttribute(TYPE_ATTR, myType);
    element.setAttribute(FQNAME_ATTR, getFQName());
    parentNode.addContent(element);
  }

  @Override
  public boolean resolve(@NotNull RefManager manager) {
    if (myRefElement != null) {
      return myRefElement instanceof RefElement && myRefElement.isValid();
    }
    myRefElement = manager.getReference(myType, getFQName());
    return myRefElement != null;
  }

  @Override
  public void freeReference() {
    myRefElement = null;
  }

  @Nullable
  private String getContainingFileName(RefElement ref) {
    SmartPsiElementPointer pointer = ref.getPointer();
    if (pointer == null) return null;
    PsiFile file = pointer.getContainingFile();
    if (file == null) return null;
    return file.getName();
  }
}
