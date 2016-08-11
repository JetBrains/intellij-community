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
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class JumpToObjectAction extends DebuggerAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.JumpToObjectAction");
  @Override
  public void actionPerformed(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode == null) {
      return;
    }

    final NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if(!(descriptor instanceof ValueDescriptor)) {
      return;
    }

    DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      return;
    }

    debugProcess.getManagerThread().schedule(new NavigateCommand(debuggerContext, (ValueDescriptor)descriptor, debugProcess, e));
  }

  @Override
  public void update(final AnActionEvent e) {
    if(!isFirstStart(e)) {
      return;
    }

    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    final NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if (descriptor instanceof ValueDescriptor) {
      debugProcess.getManagerThread().schedule(new EnableCommand(debuggerContext, (ValueDescriptor)descriptor, debugProcess, e));
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }

  private static SourcePosition calcPosition(final ValueDescriptor descriptor, final DebugProcessImpl debugProcess) throws ClassNotLoadedException {
    Type type = descriptor.getType();
    if (type == null) {
      return null;
    }

    try {
      if(type instanceof ArrayType) {
        type = ((ArrayType)type).componentType();
      }
      if(type instanceof ClassType) {
        final ClassType clsType = (ClassType)type;
        Location lambdaLocation = null;
        if (DebuggerUtilsEx.isLambdaClassName(clsType.name())) {
          List<Method> notConstructorMethods = ContainerUtil.filter(clsType.methods(), m -> !m.isConstructor());
          if (notConstructorMethods.size() == 1) {
            AtomicReference<Location> locationRef = new AtomicReference<>();
            MethodBytecodeUtil.visit(clsType, notConstructorMethods.get(0), new MethodVisitor(Opcodes.API_VERSION) {
              @Override
              public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                ReferenceType cls = ContainerUtil.getFirstItem(clsType.virtualMachine().classesByName(owner));
                if (cls != null) {
                  Method method = ContainerUtil.getFirstItem(cls.methodsByName(name));
                  if (method != null) {
                    try {
                      Location loc = ContainerUtil.getFirstItem(method.allLineLocations());
                      if (loc != null) {
                        locationRef.set(loc);
                      }
                    }
                    catch (AbsentInformationException e) {
                      LOG.debug(e);
                    }
                  }
                }
              }
            });
            lambdaLocation = locationRef.get();
          }
        }
        final Location location = lambdaLocation != null ? lambdaLocation : ContainerUtil.getFirstItem(clsType.allLineLocations());
        if (location != null) {
          return ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
            @Override
            public SourcePosition compute() {
              SourcePosition position = debugProcess.getPositionManager().getSourcePosition(location);
              // adjust position for non-anonymous classes
              if (clsType.name().indexOf('$') < 0) {
                final PsiClass classAt = JVMNameUtil.getClassAt(position);
                if (classAt != null) {
                  final SourcePosition classPosition = SourcePosition.createFromElement(classAt);
                  if (classPosition != null) {
                    position = classPosition;
                  }
                }
              }
              return position;
            }
          });
        }
      }
    }
    catch (ClassNotPreparedException | AbsentInformationException e) {
      LOG.debug(e);
    }
    return null;
  }

  public static class NavigateCommand extends SourcePositionCommand {
    public NavigateCommand(final DebuggerContextImpl debuggerContext, final ValueDescriptor descriptor, final DebugProcessImpl debugProcess, final AnActionEvent e) {
      super(debuggerContext, descriptor, debugProcess, e);
    }
    @Override
    protected NavigateCommand createRetryCommand() {
      return new NavigateCommand(myDebuggerContext, myDescriptor, myDebugProcess, myActionEvent);
    }
    @Override
    protected void doAction(final SourcePosition sourcePosition) {
      if (sourcePosition != null) {
        sourcePosition.navigate(true);
      }
    }
  }

  private static class EnableCommand extends SourcePositionCommand {
    public EnableCommand(final DebuggerContextImpl debuggerContext, final ValueDescriptor descriptor, final DebugProcessImpl debugProcess, final AnActionEvent e) {
      super(debuggerContext, descriptor, debugProcess, e);
    }
    @Override
    protected EnableCommand createRetryCommand() {
      return new EnableCommand(myDebuggerContext, myDescriptor, myDebugProcess, myActionEvent);
    }
    @Override
    protected void doAction(final SourcePosition sourcePosition) {
      enableAction(myActionEvent, sourcePosition != null);
    }
  }

  public abstract static class SourcePositionCommand extends SuspendContextCommandImpl {
    protected final DebuggerContextImpl myDebuggerContext;
    protected final ValueDescriptor myDescriptor;
    protected final DebugProcessImpl myDebugProcess;
    protected final AnActionEvent myActionEvent;

    public SourcePositionCommand(final DebuggerContextImpl debuggerContext,
                                 final ValueDescriptor descriptor,
                                 final DebugProcessImpl debugProcess,
                                 final AnActionEvent actionEvent) {
      super(debuggerContext.getSuspendContext());
      myDebuggerContext = debuggerContext;
      myDescriptor = descriptor;
      myDebugProcess = debugProcess;
      myActionEvent = actionEvent;
    }

    @Override
    public final void contextAction() throws Exception {
      try {
        doAction(calcPosition(myDescriptor, myDebugProcess));
      }
      catch (ClassNotLoadedException ex) {
        final String className = ex.className();
        if (loadClass(className) != null) {
          myDebugProcess.getManagerThread().schedule(createRetryCommand());
        }
      }
    }

    protected abstract SourcePositionCommand createRetryCommand();

    protected abstract void doAction(@Nullable SourcePosition sourcePosition);

    private ReferenceType loadClass(final String className) {
      final EvaluationContextImpl eContext = myDebuggerContext.createEvaluationContext();
      try {
        return myDebugProcess.loadClass(eContext, className, eContext.getClassLoader());
      }
      catch(Throwable ignored) {
      }
      return null;
    }
  }

}
