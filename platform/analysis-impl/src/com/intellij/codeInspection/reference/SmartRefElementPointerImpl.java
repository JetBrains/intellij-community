// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SmartRefElementPointerImpl implements SmartRefElementPointer {
  public static final @NonNls String FQNAME_ATTR = "FQNAME";
  public static final @NonNls String TYPE_ATTR = "TYPE";
  public static final @NonNls String ENTRY_POINT = "entry_point";
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

  private @Nullable String getContainingFileName(RefElement ref) {
    SmartPsiElementPointer pointer = ref.getPointer();
    if (pointer == null) return null;
    PsiFile file = pointer.getContainingFile();
    if (file == null) return null;
    return file.getName();
  }
}
