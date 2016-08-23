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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.MultiMap;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
  private static Method ourEnqueueMethod;
  private static Method ourWaitForReplyMethod;

  private static final boolean ourInitializationOkSet;
  private static Class<?> ourSlotInfoClassSet;
  private static Constructor<?> slotInfoConstructorSet;
  private static Method ourEnqueueMethodSet;
  private static Method ourWaitForReplyMethodSet;

  static {
    // get values init
    boolean success = false;
    try {
      String GetValuesClassName = "com.sun.tools.jdi.JDWP$StackFrame$GetValues";
      ourSlotInfoClass = Class.forName(GetValuesClassName + "$SlotInfo");
      slotInfoConstructor = ourSlotInfoClass.getDeclaredConstructor(int.class, byte.class);
      slotInfoConstructor.setAccessible(true);

      Class<?> ourGetValuesClass = Class.forName(GetValuesClassName);
      ourEnqueueMethod = getDeclaredMethodByName(ourGetValuesClass, "enqueueCommand");
      ourWaitForReplyMethod = getDeclaredMethodByName(ourGetValuesClass, "waitForReply");

      success = true;
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    ourInitializationOk = success;

    // set value init
    success = false;
    try {
      String setValuesClassName = "com.sun.tools.jdi.JDWP$StackFrame$SetValues";
      ourSlotInfoClassSet = Class.forName(setValuesClassName + "$SlotInfo");
      slotInfoConstructorSet = ourSlotInfoClassSet.getDeclaredConstructors()[0];
      slotInfoConstructorSet.setAccessible(true);

      Class<?> ourGetValuesClassSet = Class.forName(setValuesClassName);
      ourEnqueueMethodSet = getDeclaredMethodByName(ourGetValuesClassSet, "enqueueCommand");
      ourWaitForReplyMethodSet = getDeclaredMethodByName(ourGetValuesClassSet, "waitForReply");

      success = true;
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    ourInitializationOkSet = success;
  }

  public static Map<DecompiledLocalVariable, Value> fetchValues(@NotNull StackFrameProxyImpl frameProxy, DebugProcess process) throws Exception {
    Map<DecompiledLocalVariable, Value> map = new LinkedHashMap<>(); // LinkedHashMap for correct order

    Location location = frameProxy.location();
    com.sun.jdi.Method method = location.method();
    final int firstLocalVariableSlot = getFirstLocalsSlot(method);

    // gather code variables names
    MultiMap<Integer, String> namesMap = calcNames(new SimpleStackFrameContext(frameProxy, process), firstLocalVariableSlot);

    // first add arguments
    int slot = getFirstArgsSlot(method);
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
    List<DecompiledLocalVariable> vars = collectVariablesFromBytecode(frameProxy.getVirtualMachine(), location, namesMap);
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
    final Long frameId = ReflectionUtil.getField(frame.getClass(), frame, long.class, "id");
    final VirtualMachine vm = frame.virtualMachine();
    final Method stateMethod = ReflectionUtil.getDeclaredMethod(vm.getClass(), "state");

    Object slotInfoArray = createSlotInfoArray(vars);

    Object ps;
    final Object vmState = stateMethod.invoke(vm);
    synchronized(vmState) {
      ps = ourEnqueueMethod.invoke(null, vm, frame.thread(), frameId, slotInfoArray);
    }

    final Object reply = ourWaitForReplyMethod.invoke(null, vm, ps);
    final Value[] values = ReflectionUtil.getField(reply.getClass(), reply, Value[].class, "values");
    if (vars.size() != values.length) {
      throw new InternalException("Wrong number of values returned from target VM");
    }
    int idx = 0;
    for (DecompiledLocalVariable var : vars) {
      map.put(var, values[idx++]);
    }
    return map;
  }

  public static boolean canSetValues() {
    return ourInitializationOkSet;
  }

  public static void setValue(StackFrame frame, int slot, Value value) throws EvaluateException {
    try {
      final Long frameId = ReflectionUtil.getField(frame.getClass(), frame, long.class, "id");
      final VirtualMachine vm = frame.virtualMachine();
      final Method stateMethod = ReflectionUtil.getDeclaredMethod(vm.getClass(), "state");

      Object slotInfoArray = createSlotInfoArraySet(slot, value);

      Object ps;
      final Object vmState = stateMethod.invoke(vm);
      synchronized (vmState) {
        ps = ourEnqueueMethodSet.invoke(null, vm, frame.thread(), frameId, slotInfoArray);
      }

      ourWaitForReplyMethodSet.invoke(null, vm, ps);
    }
    catch (Exception e) {
      throw new EvaluateException("Unable to set value", e);
    }
  }

  private static Object createSlotInfoArraySet(int slot, Value value)
    throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Object arrayInstance = Array.newInstance(ourSlotInfoClassSet, 1);
    Array.set(arrayInstance, 0, slotInfoConstructorSet.newInstance(slot, value));
    return arrayInstance;
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

  private static Method getDeclaredMethodByName(Class aClass, String methodName) throws NoSuchMethodException {
    for (Method method : aClass.getDeclaredMethods()) {
      if (methodName.equals(method.getName())) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(aClass.getName() + "." + methodName);
  }

  @NotNull
  private static List<DecompiledLocalVariable> collectVariablesFromBytecode(VirtualMachineProxyImpl vm,
                                                                            Location location,
                                                                            MultiMap<Integer, String> namesMap) {
    if (!vm.canGetBytecodes()) {
      return Collections.emptyList();
    }
    try {
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
        final HashMap<Integer, DecompiledLocalVariable> usedVars = new HashMap<>();
        MethodBytecodeUtil.visit(location.declaringType(), method, location.codeIndex(),
          new MethodVisitor(Opcodes.API_VERSION) {
           @Override
           public void visitVarInsn(int opcode, int slot) {
             if (slot >= firstLocalVariableSlot) {
               DecompiledLocalVariable variable = usedVars.get(slot);
               String typeSignature = MethodBytecodeUtil.getVarInstructionType(opcode).getDescriptor();
               if (variable == null || !typeSignature.equals(variable.getSignature())) {
                 variable = new DecompiledLocalVariable(slot, false, typeSignature, namesMap.get(slot));
                 usedVars.put(slot, variable);
               }
             }
           }
          });
        if (usedVars.isEmpty()) {
          return Collections.emptyList();
        }

        List<DecompiledLocalVariable> vars = new ArrayList<>(usedVars.values());
        vars.sort(Comparator.comparingInt(DecompiledLocalVariable::getSlot));
        return vars;
      }
    }
    catch (UnsupportedOperationException ignored) {
    }
    catch (Exception e) {
      LOG.error(e);
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
            MultiMap<Integer, String> res = new MultiMap<>();
            int slot = Math.max(0, firstLocalsSlot - getParametersStackSize(method));
            for (PsiParameter parameter : DebuggerUtilsEx.getParameters(method)) {
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
    private boolean myReached = false;

    public LocalVariableNameFinder(int startSlot, MultiMap<Integer, String> names, PsiElement element) {
      myNames = names;
      myCurrentSlotIndex = startSlot;
      myElement = element;
      myIndexStack = new Stack<>();

    }

    private boolean shouldVisit(PsiElement scope) {
      return !myReached && PsiTreeUtil.isContextAncestor(scope, myElement, false);
    }

    @Override
    public void visitElement(PsiElement element) {
      if (element == myElement) {
        myReached = true;
      }
      else {
        super.visitElement(element);
      }
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (!myReached) {
        appendName(variable.getName());
        myCurrentSlotIndex += getTypeSlotSize(variable.getType());
      }
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

  private static int getParametersStackSize(PsiElement method) {
    int startSlot = 0;
    for (PsiParameter parameter : DebuggerUtilsEx.getParameters(method)) {
      startSlot += getTypeSlotSize(parameter.getType());
    }
    return startSlot;
  }

  private static int getTypeSlotSize(PsiType varType) {
    if (PsiType.DOUBLE.equals(varType) || PsiType.LONG.equals(varType)) {
      return 2;
    }
    return 1;
  }

  private static int getFirstArgsSlot(com.sun.jdi.Method method) {
    return method.isStatic() ? 0 : 1;
  }

  private static int getFirstLocalsSlot(com.sun.jdi.Method method) {
    int firstLocalVariableSlot = getFirstArgsSlot(method);
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
