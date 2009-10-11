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

package com.intellij.history.core.storage;

public class ThreadSafeContentStorage implements IContentStorage {
  private final IContentStorage mySubject;

  public ThreadSafeContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public synchronized void save() {
    mySubject.save();
  }

  public synchronized void close() {
    mySubject.close();
  }

  public synchronized int store(byte[] content) throws BrokenStorageException {
    return mySubject.store(content);
  }

  public synchronized byte[] load(int id) throws BrokenStorageException {
    return mySubject.load(id);
  }

  public synchronized void remove(int id) {
    mySubject.remove(id);
  }

  public synchronized void setVersion(final int version) {
    mySubject.setVersion(version);
  }

  public synchronized int getVersion() {
    return mySubject.getVersion();
  }
}
