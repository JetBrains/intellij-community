/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util;

/**
 * //TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 2/14/11 9:15 AM
 */
public interface SequentialTask {
  
  //TODO den implement
  void prepare();
  
  //TODO den add doc
  boolean isDone();
  
  //TODO den add doc
  boolean iteration();
}
