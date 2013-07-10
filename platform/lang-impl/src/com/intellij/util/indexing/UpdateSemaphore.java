/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.util.concurrency.Semaphore;

/**
* @author Eugene Zhuravlev
*         Date: 6/26/13
*/
final class UpdateSemaphore extends Semaphore {
  private volatile boolean myIsCanceled = false;
  
  UpdateSemaphore() {
  }
  
  boolean isUpdateCanceled() {
    return myIsCanceled;
  }
  
  void reportUpdateCanceled() {
    myIsCanceled = true;
  }
}
