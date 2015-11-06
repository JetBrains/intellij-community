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
package com.intellij.debugger.jdi;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.SimpleStackFrameContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.MultiMap;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * From JDI sources:
 *
      validateStackFrame();
      validateMirrors(variables);

      int count = variables.size();
      JDWP.StackFrame.GetValues.SlotInfo[] slots =
                         new JDWP.StackFrame.GetValues.SlotInfo[count];

      for (int i=0; i<count; ++i) {
          LocalVariableImpl variable = (LocalVariableImpl)variables.get(i);
          if (!variable.isVisible(this)) {
              throw new IllegalArgumentException(variable.name() +
                               " is not valid at this frame location");
          }
          slots[i] = new JDWP.StackFrame.GetValues.SlotInfo(variable.slot(),
                                    (byte)variable.signature().charAt(0));
      }

      PacketStream ps;

      synchronized (vm.state()) {
          validateStackFrame();
          ps = JDWP.StackFrame.GetValues.enqueueCommand(vm, thread, id, slots);
      }

      ValueImpl[] values;
      try {
          values = JDWP.StackFrame.GetValues.waitForReply(vm, ps).values;
      } catch (JDWPException exc) {
          switch (exc.errorCode()) {
              case JDWP.Error.INVALID_FRAMEID:
              case JDWP.Error.THREAD_NOT_SUSPENDED:
              case JDWP.Error.INVALID_THREAD:
                  throw new InvalidStackFrameException();
              default:
                  throw exc.toJDIException();
          }
      }

      if (count != values.length) {
          throw new InternalException(
                    "Wrong number of values returned from target VM");
      }
      Map map = new HashMap(count);
      for (int i=0; i<count; ++i) {
          LocalVariableImpl variable = (LocalVariableImpl)variables.get(i);
          map.put(variable, values[i]);
      }
      return map;
 */
public class LocalVariablesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.LocalVariablesUtil");

  private static final boolean ourInitializationOk;
  private static Class<?> ourSlotInfoClass;
  private static Constructor<?> slotInfoConstructor;
  private static Class<?> ourGetValuesClass;
  private static Method ourEnqueueMethod;
  private static Method ourWaitForReplyMethod;

  static {
    boolean success = false;
    try {
      ourSlotInfoClass = Class.forName("com.sun.tools.jdi.JDWP$StackFrame$GetValues$SlotInfo");
      slotInfoConstructor = ourSlotInfoClass.getDeclaredConstructor(int.class, byte.class);
      slotInfoConstructor.setAccessible(true);

      ourGetValuesClass = Class.forName("com.sun.tools.jdi.JDWP$StackFrame$GetValues");
      ourEnqueueMethod = findMethod(ourGetValuesClass, "enqueueCommand");
      ourEnqueueMethod.setAccessible(true);
      ourWaitForReplyMethod = findMethod(ourGetValuesClass, "waitForReply");
      ourWaitForReplyMethod.setAccessible(true);

      success = true;
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    ourInitializationOk = success;
  }

  public static Map<DecompiledLocalVariable, Value> fetchValues(@NotNull StackFrameProxyImpl frameProxy, DebugProcess process) throws Exception {
    Map<DecompiledLocalVariable, Value> map = new LinkedHashMap<DecompiledLocalVariable, Value>(); // LinkedHashMap for correct order

    com.sun.jdi.Method method = frameProxy.location().method();
    final int firstLocalVariableSlot = getFirstLocalsSlot(method);

    // gather code variables names
    MultiMap<Integer, String> namesMap = calcNames(new SimpleStackFrameContext(frameProxy, process), firstLocalVariableSlot);

    // first add arguments
    int slot = 0;
    List<String> typeNames = method.argumentTypeNames();
    List<Value> argValues = frameProxy.getArgumentValues();
    for (int i = 0; i < argValues.size(); i++) {
      map.put(new DecompiledLocalVariable(slot, true, null, namesMap.get(slot)), argValues.get(i));
      slot += getTypeSlotSize(typeNames.get(i));
    }

    if (!ourInitializationOk) {
      return map;
    }

    // now try to fetch stack values
    List<DecompiledLocalVariable> vars = collectVariablesFromBytecode(frameProxy, namesMap);
    StackFrame frame = frameProxy.getStackFrame();
    int size = vars.size();
    while (size > 0) {
      try {
        return fetchSlotValues(map, vars.subList(0, size), frame);
      }
      catch (Exception e) {
        LOG.info(e);
      }
      size--; // try with the reduced list
    }

    return map;
  }

  private static Map<DecompiledLocalVariable, Value> fetchSlotValues(Map<DecompiledLocalVariable, Value> map,
                                                                     List<DecompiledLocalVariable> vars,
                                                                     StackFrame frame) throws Exception {
    final Field frameIdField = frame.getClass().getDeclaredField("id");
    frameIdField.setAccessible(true);
    final Object frameId = frameIdField.get(frame);

    final VirtualMachine vm = frame.virtualMachine();
    final Method stateMethod = vm.getClass().getDeclaredMethod("state");
    stateMethod.setAccessible(true);

    Object slotInfoArray = createSlotInfoArray(vars);

    Object ps;
    final Object vmState = stateMethod.invoke(vm);
    synchronized(vmState) {
      ps = ourEnqueueMethod.invoke(null, vm, frame.thread(), frameId, slotInfoArray);
    }

    final Object reply = ourWaitForReplyMethod.invoke(null, vm, ps);
    final Field valuesField = reply.getClass().getDeclaredField("values");
    valuesField.setAccessible(true);
    final Value[] values = (Value[])valuesField.get(reply);
    if (vars.size() != values.length) {
      throw new InternalException("Wrong number of values returned from target VM");
    }
    int idx = 0;
    for (DecompiledLocalVariable var : vars) {
      map.put(var, values[idx++]);
    }
    return map;
  }

  private static Object createSlotInfoArray(Collection<DecompiledLocalVariable> vars) throws Exception {
    final Object arrayInstance = Array.newInstance(ourSlotInfoClass, vars.size());

    int idx = 0;
    for (DecompiledLocalVariable var : vars) {
      final Object info = slotInfoConstructor.newInstance(var.getSlot(), (byte)var.getSignature().charAt(0));
      Array.set(arrayInstance, idx++, info);
    }

    return arrayInstance;
  }

  private static Method findMethod(Class aClass, String methodName) throws NoSuchMethodException {
    for (Method method : aClass.getDeclaredMethods()) {
      if (methodName.equals(method.getName())) {
        return method;
      }
    }
    throw new NoSuchMethodException(aClass.getName() + "." + methodName);
  }

  @NotNull
  private static List<DecompiledLocalVariable> collectVariablesFromBytecode(StackFrameProxyImpl frame,
                                                                            final MultiMap<Integer, String> namesMap) throws EvaluateException {
    if (!frame.getVirtualMachine().canGetBytecodes()) {
      return Collections.emptyList();
    }
    try {
      final Location location = frame.location();
      LOG.assertTrue(location != null);
      final com.sun.jdi.Method method = location.method();
      final Location methodLocation = method.location();
      if (methodLocation == null || methodLocation.codeIndex() < 0) {
        // native or abstract method
        return Collections.emptyList();
      }

      final byte[] bytecodes = method.bytecodes();
      if (bytecodes != null && bytecodes.length > 0) {
        final int firstLocalVariableSlot = getFirstLocalsSlot(method);
        final HashMap<Integer, DecompiledLocalVariable> usedVars = new HashMap<Integer, DecompiledLocalVariable>();
        new InstructionParser(bytecodes, location.codeIndex()) {
          @Override
          protected void localVariableInstructionFound(int opcode, int slot, String typeSignature) {
            if (slot >= firstLocalVariableSlot) {
              DecompiledLocalVariable variable = usedVars.get(slot);
              if (variable == null || !typeSignature.equals(variable.getSignature())) {
                variable = new DecompiledLocalVariable(slot, false, typeSignature, namesMap.get(slot));
                usedVars.put(slot, variable);
              }
            }
          }
        }.parse();

        if (usedVars.isEmpty()) {
          return Collections.emptyList();
        }

        List<DecompiledLocalVariable> vars = new ArrayList<DecompiledLocalVariable>(usedVars.values());
        Collections.sort(vars, DecompiledLocalVariable.COMPARATOR);
        return vars;
      }
    }
    catch (UnsupportedOperationException ignored) {
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static MultiMap<Integer, String> calcNames(@NotNull final StackFrameContext context, final int firstLocalsSlot) {
    return ApplicationManager.getApplication().runReadAction(new Computable<MultiMap<Integer, String>>() {
      @Override
      public MultiMap<Integer, String> compute() {
        SourcePosition position = ContextUtil.getSourcePosition(context);
        if (position != null) {
          PsiElement element = position.getElementAt();
          PsiElement method = DebuggerUtilsEx.getContainingMethod(element);
          if (method != null) {
            PsiParameterList params = DebuggerUtilsEx.getParameterList(method);
            if (params != null) {
              MultiMap<Integer, String> res = new MultiMap<Integer, String>();
              int psiFirstLocalsSlot = getFirstLocalsSlot(method);
              int slot = Math.max(0, firstLocalsSlot - psiFirstLocalsSlot);
              for (int i = 0; i < params.getParametersCount(); i++) {
                PsiParameter parameter = params.getParameters()[i];
                res.putValue(slot, parameter.getName());
                slot += getTypeSlotSize(parameter.getType());
              }
              PsiElement body = DebuggerUtilsEx.getBody(method);
              if (body != null) {
                try {
                  body.accept(new LocalVariableNameFinder(firstLocalsSlot, res, element));
                }
                catch (Exception e) {
                  LOG.info(e);
                }
              }
              return res;
            }
          }
        }
        return MultiMap.empty();
      }
    });
  }

  /**
   * Walker that preserves the order of locals declarations but walks only visible scope
   */
  private static class LocalVariableNameFinder extends JavaRecursiveElementVisitor {
    private final MultiMap<Integer, String> myNames;
    private int myCurrentSlotIndex;
    private final PsiElement myElement;
    private final Stack<Integer> myIndexStack;

    public LocalVariableNameFinder(int startSlot, MultiMap<Integer, String> names, PsiElement element) {
      myNames = names;
      myCurrentSlotIndex = startSlot;
      myElement = element;
      myIndexStack = new Stack<Integer>();
    }

    private boolean shouldVisit(PsiElement scope) {
      return PsiTreeUtil.isContextAncestor(scope, myElement, false);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      appendName(variable.getName());
      myCurrentSlotIndex += getTypeSlotSize(variable.getType());
    }

    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      if (shouldVisit(statement)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          appendName("<monitor>");
          myCurrentSlotIndex++;
          super.visitSynchronizedStatement(statement);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    private void appendName(String varName) {
      myNames.putValue(myCurrentSlotIndex, varName);
    }

    @Override
    public void visitCodeBlock(PsiCodeBlock block) {
      if (shouldVisit(block)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          super.visitCodeBlock(block);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      if (shouldVisit(statement)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          super.visitForStatement(statement);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      if (shouldVisit(statement)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          super.visitForeachStatement(statement);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    @Override
    public void visitCatchSection(PsiCatchSection section) {
      if (shouldVisit(section)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          super.visitCatchSection(section);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    @Override
    public void visitResourceList(PsiResourceList resourceList) {
      if (shouldVisit(resourceList)) {
        myIndexStack.push(myCurrentSlotIndex);
        try {
          super.visitResourceList(resourceList);
        }
        finally {
          myCurrentSlotIndex = myIndexStack.pop();
        }
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // skip local and anonymous classes
    }
  }

  private static int getFirstLocalsSlot(PsiElement method) {
    int startSlot = 0;
    if (method instanceof PsiModifierListOwner) {
      startSlot = ((PsiModifierListOwner)method).hasModifierProperty(PsiModifier.STATIC) ? 0 : 1;
    }
    PsiParameterList params = DebuggerUtilsEx.getParameterList(method);
    if (params != null) {
      for (PsiParameter parameter : params.getParameters()) {
        startSlot += getTypeSlotSize(parameter.getType());
      }
    }
    return startSlot;
  }

  private static int getTypeSlotSize(PsiType varType) {
    if (PsiType.DOUBLE.equals(varType) || PsiType.LONG.equals(varType)) {
      return 2;
    }
    return 1;
  }

  private static int getFirstLocalsSlot(com.sun.jdi.Method method) {
    int firstLocalVariableSlot = method.isStatic() ? 0 : 1;
    for (String type : method.argumentTypeNames()) {
      firstLocalVariableSlot += getTypeSlotSize(type);
    }
    return firstLocalVariableSlot;
  }

  private static int getTypeSlotSize(String name) {
    if (PsiKeyword.DOUBLE.equals(name) || PsiKeyword.LONG.equals(name)) {
      return 2;
    }
    return 1;
  }
}
