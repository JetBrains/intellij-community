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
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class InferenceSessionContainer {
  private final Map<PsiElement, InferenceSession> myNestedSessions = new HashMap<PsiElement, InferenceSession>();

  public InferenceSessionContainer() {
  }

  public void registerNestedSession(InferenceSession targetSession, InferenceSession session) {
    targetSession.propagateVariables(session.getInferenceVariables());
    myNestedSessions.put(session.getContext(), session);
    myNestedSessions.putAll(session.getInferenceSessionContainer().myNestedSessions);
  }

  @Contract("_, !null -> !null")
  public InferenceSession findNestedCallSession(PsiElement arg, @Nullable InferenceSession defaultSession) {
    InferenceSession session = myNestedSessions.get(PsiTreeUtil.getParentOfType(arg, PsiCall.class));
    return session == null ? defaultSession : session;
  }
}