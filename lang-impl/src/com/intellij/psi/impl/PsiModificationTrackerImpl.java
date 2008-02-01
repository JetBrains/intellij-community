/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 5:57:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;

public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiModificationTrackerImpl");

  private PsiManager myManager;
  private long myModificationCount = 0;
  private long myOutOfCodeBlockModificationCount = 0;
  private long myJavaStructureModificationCount = 0;

  public PsiModificationTrackerImpl(PsiManager manager) {
    myManager = manager;
  }

  public void incCounter(){
    myModificationCount++;
    myOutOfCodeBlockModificationCount++;
    myJavaStructureModificationCount++;
  }

  public void incOutOfCodeBlockModificationCounter() {
    myOutOfCodeBlockModificationCount++;
  }

  public void treeChanged(PsiTreeChangeEventImpl event) {
    myModificationCount++;
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount;
  }

  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationCount;
  }
}
