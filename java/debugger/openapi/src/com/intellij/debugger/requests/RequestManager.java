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
package com.intellij.debugger.requests;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.Nullable;

public interface RequestManager {
  void callbackOnPrepareClasses(ClassPrepareRequestor requestor, String classOrPatternToBeLoaded);
  void callbackOnPrepareClasses(ClassPrepareRequestor requestor, SourcePosition classPosition) throws EvaluateException;

  @Nullable
  ClassPrepareRequest createClassPrepareRequest(ClassPrepareRequestor requestor, String pattern);  

  void enableRequest(EventRequest request);

  void setInvalid(Requestor requestor, String message);
}
