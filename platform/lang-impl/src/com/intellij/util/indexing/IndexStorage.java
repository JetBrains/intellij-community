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

package com.intellij.util.indexing;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.Flushable;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface IndexStorage<Key, Value> extends Flushable {
  
  void addValue(Key key, int inputId, Value value) throws StorageException;

  void removeValue(Key key, int inputId, Value value) throws StorageException;

  void removeAllValues(Key key, int inputId) throws StorageException;

  void clear() throws StorageException;
  
  @NotNull
  ValueContainer<Value> read(Key key) throws StorageException;

  boolean processKeys(Processor<Key> processor) throws StorageException;

  Collection<Key> getKeysWithValues() throws StorageException;

  Collection<Key> getKeys() throws StorageException;

  void close() throws StorageException;

  void flush() throws IOException;
}
