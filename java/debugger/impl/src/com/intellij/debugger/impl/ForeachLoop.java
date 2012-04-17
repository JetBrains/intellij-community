/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.EvaluationDescriptor;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 21.02.12
 * Time: 4:29
 * To change this template use File | Settings | File Templates.
 */
public class ForeachLoop {
  private PsiForeachStatement myForeach;
  private final String myCollectionText;

  private EvaluationContext myContext;

  private boolean myIsArray;

  @Nullable
  private Integer myCurrentIndex;

  public ForeachLoop(@NotNull PsiForeachStatement foreach, @NotNull EvaluationContext context) {
    myForeach = foreach;
    myContext = context;
    final PsiExpression collection = myForeach.getIteratedValue();
    if (collection.getType() instanceof PsiArrayType){
      myIsArray = true;
    }
    myCollectionText = collection.getText();
  }

  public boolean isIteratedCollection(@NotNull final ValueDescriptor descriptor, @NotNull final EvaluationContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (!myContext.equals(context)) return false;

        if (myIsArray) {
          final Value arr = getLocalVar(context, "arr$");
          if (arr != null && arr.equals(descriptor.getValue())) {
            myCurrentIndex = getCurrentArrayIterationIndex(context);
            return true;
          }
        } else if (descriptor instanceof WatchItemDescriptor) {
          final PsiElement origin = ((WatchItemDescriptor)descriptor).getOrigin();
          if (origin != null && origin.equals(myForeach.getIteratedValue())) {
            return true;
          }
        }
        if (descriptor instanceof EvaluationDescriptor) {
          if (myCollectionText.equals(((EvaluationDescriptor)descriptor).getEvaluationText().getText())) {
            return true;
          }
        }
        if (descriptor instanceof LocalVariableDescriptor) {
          if (descriptor.getName().equals(myCollectionText)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public boolean isCurrentElement(int index, final Value value, final EvaluationContext context) {
    if (myIsArray) {
      if (myCurrentIndex != null) {
        return index == myCurrentIndex;
      }
    } else {
      if (value instanceof ObjectReference) {
        return value.equals(getCurrentIterationParameterValue(context));
      }
    }
    return false;
  }

  @Nullable
  private ObjectReference getCurrentIterationParameterValue(EvaluationContext context) {
    final String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myForeach.getIterationParameter().getName();
      }
    });
    final Value var = getLocalVar(context, name);
    return var instanceof ObjectReference ? (ObjectReference)var : null;
  }

  @Nullable
  private static Value getLocalVar(EvaluationContext context, String name) {
    try {
      final LocalVariableProxyImpl variable = (LocalVariableProxyImpl)context.getFrameProxy().visibleVariableByName(name);
      if (variable != null) {
        return ((StackFrameProxyImpl)(context.getFrameProxy())).getValue(variable);
      } else {
        return null;
      }
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public SourcePosition getFirstBodyStatementPosition() {
    return ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
      @Override
      public SourcePosition compute() {
        return SourcePosition.createFromElement(getFirstBodyStatement());
      }
    });
  }

  public boolean isMultiline() {
    final PsiStatement statement = getFirstBodyStatement();
    if (statement == null) {
      return false;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final SourcePosition foreach = SourcePosition.createFromElement(myForeach);
        final SourcePosition firstStatement = SourcePosition.createFromElement(statement);
        return foreach.getLine() != firstStatement.getLine();
      }
    });
  }

  private PsiStatement getFirstBodyStatement() {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<PsiStatement>() {
      @Override
      public PsiStatement compute() {
        PsiStatement firstStatement = null;

        final PsiStatement body = myForeach.getBody();
        if (body instanceof PsiBlockStatement) {
          final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
          if (statements.length > 0) {
            firstStatement = statements[0];
          }
        } else {
          firstStatement = body;
        }
        return firstStatement;
      }
    });
  }

  public ForeachState getFutureState(ArrayElementDescriptorImpl descriptor) {
    if (myIsArray) {
      return new ForeachState(true, descriptor.getIndex(), 0, myForeach);
    } else {
      final ObjectReference object = (ObjectReference)descriptor.getValue();
      return new ForeachState(false, 0, object.uniqueID(), myForeach);
    }
  }

  public boolean checkState(ForeachState state, EvaluationContext context) {
    if (state.isArray() != myIsArray) {
      return false;
    }
    if (state.myForeach.getElement() != myForeach) {
      return false;
    }
    if (state.isArray()) {
      final Integer index = getCurrentArrayIterationIndex(context);
      return index != null && state.getIndex() == index;
    } else {
      final ObjectReference currentValue = getCurrentIterationParameterValue(context);
      return currentValue != null && currentValue.uniqueID() == state.getIdentity();
    }
  }

  @Nullable
  private static Integer getCurrentArrayIterationIndex(EvaluationContext context) {
    final Value i = getLocalVar(context, "i$");
    if (i != null && i instanceof IntegerValue) {
      return ((IntegerValue)i).value();
    }
    return null;
  }

  public static class ForeachState {
    boolean myIsArray;
    int myIndex;
    long myIdentity;
    private SmartPsiElementPointer<PsiForeachStatement> myForeach;

    public ForeachState(boolean isArray, int index, long identity, PsiForeachStatement foreach) {
      myIsArray = isArray;
      myIndex = index;
      myIdentity = identity;
      myForeach = SmartPointerManager.getInstance(foreach.getProject()).createSmartPsiElementPointer(foreach);
    }

    public boolean isArray() {
      return myIsArray;
    }

    public int getIndex() {
      return myIndex;
    }

    public long getIdentity() {
      return myIdentity;
    }
  }
}
