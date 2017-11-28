/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class RefElementImpl extends RefEntityImpl implements RefElement {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefElement");

  private static final int IS_ENTRY_MASK = 0x80;
  private static final int IS_PERMANENT_ENTRY_MASK = 0x100;


  private final SmartPsiElementPointer myID;

  private List<RefElement> myOutReferences; // guarded by this
  private List<RefElement> myInReferences; // guarded by this

  private String[] mySuppressions;

  private volatile boolean myIsDeleted;
  protected static final int IS_REACHABLE_MASK = 0x40;

  protected RefElementImpl(@NotNull String name, @NotNull RefElement owner) {
    super(name, owner.getRefManager());
    myID = null;
    myFlags = 0;
  }

  protected RefElementImpl(PsiFile file, RefManager manager) {
    this(file.getName(), file, manager);
  }

  protected RefElementImpl(@NotNull String name, @NotNull PsiElement element, @NotNull RefManager manager) {
    super(name, manager);
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(element);
    myFlags = 0;
  }

  protected boolean isDeleted() {
    return myIsDeleted;
  }

  @Override
  public boolean isValid() {
    if (myIsDeleted) return false;
    return ReadAction.compute(() -> {
      if (getRefManager().getProject().isDisposed()) return false;

      final PsiFile file = myID.getContainingFile();
      //no need to check resolve in offline mode
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return file != null && file.isPhysical();
      }

      final PsiElement element = getElement();
      return element != null && element.isPhysical();
    });
  }

  @Override
  @Nullable
  public Icon getIcon(final boolean expanded) {
    final PsiElement element = getElement();
    if (element != null && element.isValid()) {
      return element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }
    return null;
  }

  @Override
  public RefModule getModule() {
    final RefEntity owner = getOwner();
    return owner instanceof RefElement ? ((RefElement)owner).getModule() : null;
  }

  @Override
  public String getExternalName() {
    return getName();
  }

  @Override
  @Nullable
  public PsiElement getElement() {
    return myID.getElement();
  }

  @Nullable
  public PsiFile getContainingFile() {
    return myID.getContainingFile();
  }

  public VirtualFile getVirtualFile() {
    return myID.getVirtualFile();
  }

  @Override
  public SmartPsiElementPointer getPointer() {
    return myID;
  }

  public void buildReferences() {
  }

  @Override
  public boolean isReachable() {
    return checkFlag(IS_REACHABLE_MASK);
  }

  @Override
  public boolean isReferenced() {
    return !getInReferences().isEmpty();
  }

  public boolean hasSuspiciousCallers() {
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious()) return true;
    }

    return false;
  }

  @Override
  @NotNull
  public synchronized Collection<RefElement> getOutReferences() {
    return ObjectUtils.notNull(myOutReferences, ContainerUtil.emptyList());
  }

  @Override
  @NotNull
  public synchronized Collection<RefElement> getInReferences() {
    return ObjectUtils.notNull(myInReferences, ContainerUtil.emptyList());
  }

  synchronized void addInReference(RefElement refElement) {
    List<RefElement> inReferences = myInReferences;
    if (inReferences == null){
      myInReferences = inReferences = new ArrayList<>(1);
    }
    if (!inReferences.contains(refElement)) {
      inReferences.add(refElement);
    }
  }

  synchronized void addOutReference(RefElement refElement) {
    List<RefElement> outReferences = myOutReferences;
    if (outReferences == null){
      myOutReferences = outReferences = new ArrayList<>(1);
    }
    if (!outReferences.contains(refElement)) {
      outReferences.add(refElement);
    }
  }

  public void setEntry(boolean entry) {
    setFlag(entry, IS_ENTRY_MASK);
  }

  @Override
  public boolean isEntry() {
    return checkFlag(IS_ENTRY_MASK);
  }

  @Override
  public boolean isPermanentEntry() {
    return checkFlag(IS_PERMANENT_ENTRY_MASK);
  }


  @Override
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
  public String getURL() {
    final PsiElement element = getElement();
    if (element == null || !element.isPhysical()) return null;
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;
    return virtualFile.getUrl() + "#" + element.getTextOffset();
  }

  protected abstract void initialize();

  void addSuppression(final String text) {
    mySuppressions = text.split("[, ]");
  }

  public boolean isSuppressed(@NotNull String... toolId) {
    if (mySuppressions != null) {
      for (@NonNls String suppression : mySuppressions) {
        for (String id : toolId) {
          if (suppression.equals(id)) return true;
        }
        if (suppression.equalsIgnoreCase(SuppressionUtil.ALL)){
          return true;
        }
      }
    }
    final RefEntity entity = getOwner();
    return entity instanceof RefElementImpl && ((RefElementImpl)entity).isSuppressed(toolId);
  }
}
