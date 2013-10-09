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
package com.intellij.debugger.jdi;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.InternalException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

  public static Map<DecompiledLocalVariable, Value> fetchValues(StackFrame frame, Collection<DecompiledLocalVariable> vars) throws Exception {
    if (!ourInitializationOk) {
      return Collections.emptyMap();
    }
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

    try {
      final Object reply = ourWaitForReplyMethod.invoke(null, vm, ps);
      final Field valuesField = reply.getClass().getDeclaredField("values");
      valuesField.setAccessible(true);
      final Value[] values = (Value[])valuesField.get(reply);
      if (vars.size() != values.length) {
        throw new InternalException("Wrong number of values returned from target VM");
      }
      final Map<DecompiledLocalVariable, Value> map = new HashMap<DecompiledLocalVariable, Value>(vars.size());
      int idx = 0;
      for (DecompiledLocalVariable var : vars) {
        map.put(var, values[idx++]);
      }
      return map;
    }
    catch (InvocationTargetException e) {
      final Throwable target = e.getTargetException();
      if (target instanceof Exception) {
        throw (Exception)target;
      }
      throw e;
    }
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

}
