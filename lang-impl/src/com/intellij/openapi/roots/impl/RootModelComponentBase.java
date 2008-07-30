package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;


/**
 *  @author dsl
 */
public abstract class RootModelComponentBase implements Disposable {
  private final RootModelImpl myRootModel;
  private boolean myDisposed;

  //simulate System.identityHashCode()
  private static int ourInstanceCounter = 0;
  private final int myInstanceCreationIndex;

  RootModelComponentBase(RootModelImpl rootModel) {
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    myInstanceCreationIndex = ourInstanceCounter++;

    rootModel.registerOnDispose(this);
    myRootModel = rootModel;
  }

  public final int hashCode() {
    return myInstanceCreationIndex;
  }

  @Override
  public final boolean equals(Object obj) {
    return obj == this;
  }

  protected void projectOpened() {

  }

  protected void projectClosed() {

  }

  protected void moduleAdded() {

  }

  public RootModelImpl getRootModel() {
    return myRootModel;
  }

  public void dispose() {
    myDisposed = true;
  }

  protected boolean isDisposed() {
    return myDisposed;
  }
}
