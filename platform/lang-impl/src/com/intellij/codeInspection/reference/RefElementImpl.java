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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:28:53 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

public abstract class RefElementImpl extends RefEntityImpl implements RefElement {
  private static final ArrayList<RefElement> EMPTY_REFERNCES_LIST = new ArrayList<RefElement>(0);
  protected static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefElement");

  private static final int IS_ENTRY_MASK = 0x80;
  private static final int IS_PERMANENT_ENTRY_MASK = 0x100;


  private final SmartPsiElementPointer myID;

  private ArrayList<RefElement> myOutReferences;
  private ArrayList<RefElement> myInReferences;

  private String[] mySuppressions = null;

  private boolean myIsDeleted ;
  private final Module myModule;
  protected static final int IS_REACHABLE_MASK = 0x40;

  protected RefElementImpl(String name, RefElement owner) {
    super(name, owner.getRefManager());
    myID = null;
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(owner.getElement());
  }

  protected RefElementImpl(PsiFile file, RefManager manager) {
    super(file.getName(), manager);
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(file);
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(file);
  }

  protected RefElementImpl(String name, PsiElement element, RefManager manager) {
    super(name, manager);
    myID = createPointer(element, manager);
    myFlags = 0;
    myModule = ModuleUtil.findModuleForPsiElement(element);
  }

  protected SmartPsiElementPointer<PsiElement> createPointer(final PsiElement element, final RefManager manager) {
    return SmartPointerManager.getInstance(manager.getProject()).createLazyPointer(element);
  }

  public boolean isValid() {
    if (myIsDeleted) return false;
    final PsiElement element = getElement();
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return element != null && element.isPhysical();
      }
    }).booleanValue();
  }

  @Nullable
  public Icon getIcon(final boolean expanded) {
    final PsiElement element = getElement();
    if (element != null && element.isValid()) {
      return element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }
    return null;
  }

  public RefModule getModule() {
    return myManager.getRefModule(myModule);
  }

  public String getExternalName() {
    return getName();
  }

  @Nullable
  public PsiElement getElement() {
    return myID.getElement();
  }

  @Nullable
  public PsiFile getContainingFile() {
    return myID.getContainingFile();
  }

  public SmartPsiElementPointer getPointer() {
    return myID;
  }

  public void buildReferences() {
  }

  public boolean isReachable() {
    return checkFlag(IS_REACHABLE_MASK);
  }

  public boolean isReferenced() {
    return !getInReferences().isEmpty();
  }

  public boolean hasSuspiciousCallers() {
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious()) return true;
    }

    return false;
  }

  @NotNull
  public Collection<RefElement> getOutReferences() {
    if (myOutReferences == null){
      return EMPTY_REFERNCES_LIST;
    }
    return myOutReferences;
  }

  @NotNull
  public Collection<RefElement> getInReferences() {
    if (myInReferences == null){
      return EMPTY_REFERNCES_LIST;
    }
    return myInReferences;
  }

  public void addInReference(RefElement refElement) {
    if (!getInReferences().contains(refElement)) {
      if (myInReferences == null){
        myInReferences = new ArrayList<RefElement>(1);
      }
      myInReferences.add(refElement);
    }
  }

  public void addOutReference(RefElement refElement) {
    if (!getOutReferences().contains(refElement)) {
      if (myOutReferences == null){
        myOutReferences = new ArrayList<RefElement>(1);
      }
      myOutReferences.add(refElement);
    }
  }

  public void setEntry(boolean entry) {
    setFlag(entry, IS_ENTRY_MASK);
  }

  public boolean isEntry() {
    return checkFlag(IS_ENTRY_MASK);
  }

  public boolean isPermanentEntry() {
    return checkFlag(IS_PERMANENT_ENTRY_MASK);
  }


  @NotNull
  public RefElement getContainingEntry() {
    return this;
  }

  public void setPermanentEntry(boolean permanentEntry) {
    setFlag(permanentEntry, IS_PERMANENT_ENTRY_MASK);
  }

  public boolean isSuspicious() {
    return !isReachable();
  }

  public void referenceRemoved() {
    myIsDeleted = true;
    if (getOwner() != null) {
      ((RefEntityImpl)getOwner()).removeChild(this);
    }

    for (RefElement refCallee : getOutReferences()) {
      refCallee.getInReferences().remove(this);
    }

    for (RefElement refCaller : getInReferences()) {
      refCaller.getOutReferences().remove(this);
    }
  }

  @Nullable
  public URL getURL() {
    try {
      final PsiElement element = getElement();
      if (element == null) return null;
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return null;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return null;
      return new URL(virtualFile.getUrl() + "#" + element.getTextOffset());
    } catch (MalformedURLException e) {
      LOG.error(e);
    }

    return null;
  }

  protected abstract void initialize();

  public void addSuppression(final String text) {
    mySuppressions = text.split("[, ]");    
  }

  public boolean isSuppressed(final String toolId) {
    if (mySuppressions != null) {
      for (@NonNls String suppression : mySuppressions) {
        if (suppression.equals(toolId) || suppression.equals(SuppressionUtil.ALL)){
          return true;
        }
      }
    }
    final RefEntity entity = getOwner();
    return entity instanceof RefElementImpl && ((RefElementImpl)entity).isSuppressed(toolId);
  }
}
