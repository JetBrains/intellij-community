/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;

public class RefDirectoryImpl extends RefElementImpl implements RefDirectory{
  protected RefDirectoryImpl(PsiDirectory psiElement, RefManager refManager) {
    super(psiElement.getName(), psiElement, refManager);
    final PsiDirectory parentDirectory = psiElement.getParentDirectory();
    if (parentDirectory != null && PsiManager.getInstance(parentDirectory.getProject()).isInProject(parentDirectory)) {
      final RefElementImpl refElement = (RefElementImpl)refManager.getReference(parentDirectory);
      if (refElement != null) {
        refElement.add(this);
        return;
      }
    }
    final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
    if (module != null) {
      final RefModuleImpl refModule = (RefModuleImpl)refManager.getRefModule(module);
      if (refModule != null) {
        refModule.add(this);
        return;
      }
    }
    ((RefProjectImpl)refManager.getRefProject()).add(this);
  }

  public void accept(final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        visitor.visitDirectory(RefDirectoryImpl.this);
      }
    });
  }

  protected void initialize() {
    getRefManager().fireNodeInitialized(this);
  }

  public String getQualifiedName() {
    return getName(); //todo relative name
  }

  public String getExternalName() {
    final PsiElement element = getElement();
    assert element != null;
    return ((PsiDirectory)element).getVirtualFile().getPath();
  }
}