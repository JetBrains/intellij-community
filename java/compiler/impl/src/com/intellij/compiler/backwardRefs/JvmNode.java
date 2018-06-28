// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.pwa.ClassFileSymbol;

import java.util.List;

public class JvmNode {
  private final ClassFileSymbol mySymbol;
  private final List<JvmNode> myInRefs = new SmartList<>();
  private final List<JvmNode> myOutRefs = new SmartList<>();
  private volatile boolean myImplicitlyUsed;
  private volatile boolean myUsedInExternalCode;
  private final ClearableLazyValue<Boolean> myUsed = new ClearableLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      if (myImplicitlyUsed || myUsedInExternalCode) {
        return Boolean.TRUE;
      }

      for (JvmNode ref : myInRefs) {
        if (ref.myUsed.getValue() == Boolean.TRUE) {
          return Boolean.TRUE;
        }
      }

      return Boolean.FALSE;
    }
  };

  public JvmNode(ClassFileSymbol symbol) {mySymbol = symbol;}

  public void usedIn(JvmNode other) {
    myInRefs.add(other);
    other.myOutRefs.add(this);
  }

  public void setImplicitlyUsed() {
    myImplicitlyUsed = true;
  }

  public void setUsedInExternalCode() {
    myUsedInExternalCode = true;
  }

  public boolean isUsed() {
    return myUsed.getValue().booleanValue();
  }

  public ClassFileSymbol getSymbol() {
    return mySymbol;
  }
}
