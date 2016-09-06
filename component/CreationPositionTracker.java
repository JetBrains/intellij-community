package org.jetbrains.debugger.memory.component;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CreationPositionTracker extends AbstractProjectComponent {
  private final Map<XDebugSession, Map<ObjectReference, List<StackFrameDescriptor>>> mySession2Reference2Stack
      = new ConcurrentHashMap<>();

  public CreationPositionTracker(Project project) {
    super(project);
  }

  public static CreationPositionTracker getInstance(@NotNull Project project) {
    return project.getComponent(CreationPositionTracker.class);
  }

  @Nullable
  public List<StackFrameDescriptor> getStack(@NotNull XDebugSession session, @NotNull ObjectReference ref) {
    Map<ObjectReference, List<StackFrameDescriptor>> ref2Stack = mySession2Reference2Stack.getOrDefault(session, null);
    List<StackFrameDescriptor> stack = ref2Stack != null ? ref2Stack.getOrDefault(ref, null) : null;
    return stack != null ? Collections.unmodifiableList(stack) : null;
  }

  public void addStack(@NotNull XDebugSession session, @NotNull ObjectReference ref,
                       @NotNull List<StackFrameDescriptor> stack) {
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
    if(!mySession2Reference2Stack.containsKey(session)) {
      return;
    }

    mySession2Reference2Stack.put(session, new ConcurrentHashMap<>());
  }
}
