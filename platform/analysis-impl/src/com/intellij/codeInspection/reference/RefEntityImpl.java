/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.BitUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class RefEntityImpl implements RefEntity {
  private volatile RefEntityImpl myOwner;
  protected List<RefEntity> myChildren;  // guarded by this
  private final String myName;
  private Map<Key, Object> myUserMap;    // guarded by this
  protected long myFlags;
  protected final RefManagerImpl myManager;

  RefEntityImpl(@NotNull String name, @NotNull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = myManager.internName(name);
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
  public synchronized List<RefEntity> getChildren() {
    return myChildren;
  }

  @Override
  public RefEntity getOwner() {
    return myOwner;
  }

  protected void setOwner(@Nullable final RefEntityImpl owner) {
    myOwner = owner;
  }

  public synchronized void add(@NotNull final RefEntity child) {
    if (myChildren == null) {
      myChildren = new ArrayList<>(1);
    }

    myChildren.add(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  protected synchronized void removeChild(@NotNull final RefEntity child) {
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
    ApplicationManager.getApplication().runReadAction(() -> refVisitor.visitElement(this));
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value){
    synchronized(this){
      if (myUserMap == null){
        if (value == null) return;
        myUserMap = new THashMap<>();
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

  public boolean checkFlag(long mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  public void setFlag(final boolean value, final long mask) {
    myFlags = BitUtil.set(myFlags, mask, value);
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
