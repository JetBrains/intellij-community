package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;


/**
 *  @author dsl
 */
public abstract class RootModelComponentBase implements Disposable {
  private final RootModelImpl myRootModel;
  private boolean myDisposed;

  RootModelComponentBase(RootModelImpl rootModel) {
    rootModel.registerOnDispose(this);
    myRootModel = rootModel;
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
