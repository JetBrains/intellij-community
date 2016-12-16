/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.component;

import com.intellij.debugger.memory.utils.StackFrameDescriptor;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CreationPositionTracker extends AbstractProjectComponent {
  /**
   * Stores all tracked instance for each debug session.
   */
  private final ConcurrentHashMap<XDebugSession, Map<ObjectReference, List<StackFrameDescriptor>>>
      mySession2Reference2Stack = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<XDebugSession, Map<ObjectReference, List<StackFrameDescriptor>>>
      myPinnedSession2Reference2Stack = new ConcurrentHashMap<>();

  public CreationPositionTracker(Project project) {
    super(project);
  }

  @Nullable
  public static CreationPositionTracker getInstance(@NotNull Project project) {
    return project.isDisposed() ? null : project.getComponent(CreationPositionTracker.class);
  }

  @Nullable
  public List<StackFrameDescriptor> getStack(@NotNull XDebugSession session, @NotNull ObjectReference ref) {
    List<StackFrameDescriptor> stack = extract(mySession2Reference2Stack, session, ref);
    return stack != null ? stack : extract(myPinnedSession2Reference2Stack, session, ref);
  }

  public void addStack(@NotNull XDebugSession session, @NotNull ObjectReference ref,
                       @NotNull List< StackFrameDescriptor> stack) {
    if (!mySession2Reference2Stack.containsKey(session)) {
      mySession2Reference2Stack.put(session, new ConcurrentHashMap<>());
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionStopped() {
          mySession2Reference2Stack.remove(session);
        }
      });
    }

    mySession2Reference2Stack.get(session).put(ref, stack);
  }

  public void releaseBySession(@NotNull XDebugSession session) {
    if(mySession2Reference2Stack.containsKey(session)) {
      mySession2Reference2Stack.put(session, new ConcurrentHashMap<>());
    }
  }

  public void unpinStacks(@NotNull XDebugSession session, @NotNull ReferenceType ref) {
    Map<ObjectReference, List<StackFrameDescriptor>> ref2Stack = myPinnedSession2Reference2Stack.getOrDefault(session, null);
    if (ref2Stack != null) {
      Iterator<ObjectReference> iterator = ref2Stack.keySet().iterator();
      while (iterator.hasNext()) {
        ObjectReference reference = iterator.next();
        if (ref.equals(reference.referenceType())) {
          iterator.remove();
        }
      }

      if (ref2Stack.isEmpty()) {
        myPinnedSession2Reference2Stack.remove(session);
      }
    }
  }

  public void pinStacks(@NotNull XDebugSession session, @NotNull ReferenceType ref) {
    Map<ObjectReference, List<StackFrameDescriptor>> ref2Stack = mySession2Reference2Stack.getOrDefault(session, null);
    if (ref2Stack != null) {
      Map<ObjectReference, List<StackFrameDescriptor>> ref2StacksByReferenceType = ref2Stack.entrySet().stream()
          .filter(entry -> ref.equals(entry.getKey().referenceType()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      myPinnedSession2Reference2Stack.put(session, ref2StacksByReferenceType);
    }
  }

  @Nullable
  private static<T> T extract(@NotNull Map<XDebugSession, Map<ObjectReference, T>> map,
                              @NotNull XDebugSession session, @NotNull ObjectReference ref) {
    Map<ObjectReference, T> ref2something = map.getOrDefault(session, null);
    if(ref2something != null) {
      return ref2something.getOrDefault(ref, null);
    }

    return null;
  }
}
