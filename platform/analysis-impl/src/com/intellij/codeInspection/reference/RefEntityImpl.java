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
 * Date: Nov 15, 2001
 * Time: 5:14:35 PM
 * To change template for new interface use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class RefEntityImpl implements RefEntity {
  private static final String NO_NAME = InspectionsBundle.message("inspection.reference.noname");
  private RefEntityImpl myOwner;
  protected List<RefEntity> myChildren;
  private final String myName;
  private Map<Key, Object> myUserMap;
  protected int myFlags = 0;
  protected final RefManagerImpl myManager;

  protected RefEntityImpl(String name, @NotNull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = name != null ? name : NO_NAME;
    myOwner = null;
    myChildren = null;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myName;
  }

  @Override
  public List<RefEntity> getChildren() {
    return myChildren;
  }

  @Override
  public RefEntity getOwner() {
    return myOwner;
  }

  protected void setOwner(RefEntityImpl owner) {
    myOwner = owner;
  }

  public void add(RefEntity child) {
    if (myChildren == null) {
      myChildren = new ArrayList<RefEntity>(1);
    }

    myChildren.add(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  protected void removeChild(RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
      ((RefEntityImpl)child).setOwner(null);
    }
  }

  public String toString() {
    return getName();
  }

 @Override
 @Nullable
  public <T> T getUserData(@NotNull Key<T> key){
    synchronized(this){
      if (myUserMap == null) return null;
      //noinspection unchecked
      return (T)myUserMap.get(key);
    }
  }

  @Override
  public void accept(@NotNull final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        refVisitor.visitElement(RefEntityImpl.this);
      }
    });
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value){
    synchronized(this){
      if (myUserMap == null){
        if (value == null) return;
        myUserMap = new THashMap<Key, Object>();
      }
      if (value != null){
        //noinspection unchecked
        myUserMap.put(key, value);
      }
      else{
        myUserMap.remove(key);
        if (myUserMap.isEmpty()){
          myUserMap = null;
        }
      }
    }
  }

  public boolean checkFlag(int mask) {
    return (myFlags & mask) != 0;
  }

  public void setFlag(boolean b, int mask) {
    if (b) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
  }

  @Override
  public String getExternalName() {
    return myName;
  }

  @NotNull
  @Override
  public RefManagerImpl getRefManager() {
    return myManager;
  }
}
