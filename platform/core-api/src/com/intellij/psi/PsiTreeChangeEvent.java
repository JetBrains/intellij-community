// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

/**
 * Provides information about a change in the PSI tree of a project.<p/>
 *
 * Try to avoid processing PSI events at all cost! It's very hard to do this correctly and handle all edge cases.
 * Please use {@link com.intellij.psi.util.CachedValue} with {@link com.intellij.psi.util.PsiModificationTracker#MODIFICATION_COUNT}
 * or VFS events where possible. Here are just some of the complications with PSI events:
 * <ul>
 *   <li>Don't hope that if you replaced just one letter in an identifier, you'll get a "replaced" event about that identifier.
 *   You might as well get anything, e.g. a "replaced" event for the whole FileElement.</li>
 *   <li>Or not even that: you might get "propertyChanged" with {@link #PROP_UNLOADED_PSI}, not mentioning your file at all.</li>
 *   <li>Before-/after-events aren't necessarily paired: you could get several "beforeChildDeletion" and then only "childrenChanged".</li>
 *   <li>In event handler, you should be very careful to avoid traversing invalid PSI or expanding lazy-parseable elements.</li>
 *   <li>There's no specification, and the precise events you get can be changed in future
 *   as the infrastructure algorithms are improved or bugs are fixed.</li>
 *   <li>To say nothing of the fact that the precise events already depend on file size and the unpredictable activity of garbage collector,
 *   so events in production might differ from the ones you've seen in test environment.</li>
 * </ul>
 *
 * @see PsiTreeChangeListener
 */
public abstract class PsiTreeChangeEvent extends EventObject {
  public static final @NonNls String PROP_FILE_NAME = "fileName";
  public static final @NonNls String PROP_DIRECTORY_NAME  = "directoryName";
  public static final @NonNls String PROP_WRITABLE = "writable";

  public static final @NonNls String PROP_ROOTS = "roots";

  public static final @NonNls String PROP_FILE_TYPES = "propFileTypes";

  /**
   * A property change event with this property is fired when some change (e.g. VFS) somewhere in the project has occurred,
   * and there was no PSI loaded for that area, so no more specific events about that PSI can be generated. Given the absence
   * of specific information, the most likely strategy for listeners is to clear all their cache.
   */
  public static final @NonNls String PROP_UNLOADED_PSI = "propUnloadedPsi";

  protected PsiElement myParent;
  protected PsiElement myOldParent;
  protected PsiElement myNewParent;
  protected PsiElement myChild;
  protected PsiElement myOldChild;
  protected PsiElement myNewChild;

  protected PsiFile myFile;
  protected int myOffset;
  protected int myOldLength;

  protected PsiElement myElement;
  protected String myPropertyName;
  protected Object myOldValue;
  protected Object myNewValue;

  protected PsiTreeChangeEvent(PsiManager manager) {
    super(manager);
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiElement getOldParent() {
    return myOldParent;
  }

  public PsiElement getNewParent() {
    return myNewParent;
  }

  public PsiElement getChild() {
    return myChild;
  }

  public PsiElement getOldChild() {
    return myOldChild;
  }

  public PsiElement getNewChild() {
    return myNewChild;
  }

  public PsiElement getElement(){
    return myElement;
  }

  public String getPropertyName(){
    return myPropertyName;
  }

  public Object getOldValue(){
    return myOldValue;
  }

  public Object getNewValue(){
    return myNewValue;
  }

  public @Nullable PsiFile getFile() {
    return myFile;
  }
}

