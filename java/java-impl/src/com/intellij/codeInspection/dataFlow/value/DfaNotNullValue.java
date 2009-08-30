/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:45:14 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class DfaNotNullValue extends DfaValue {
  public static class Factory {
    private final DfaNotNullValue mySharedInstance;
    private final HashMap<String,ArrayList<DfaNotNullValue>> myStringToObject;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      mySharedInstance = new DfaNotNullValue(factory);
      myStringToObject = new HashMap<String, ArrayList<DfaNotNullValue>>();
    }

    @NotNull
    public DfaValue create(PsiType type) {
      if (type == null) return DfaUnknownValue.getInstance();
      mySharedInstance.myType = type;

      String id = mySharedInstance.toString();
      ArrayList<DfaNotNullValue> conditions = myStringToObject.get(id);
      if (conditions == null) {
        conditions = new ArrayList<DfaNotNullValue>();
        myStringToObject.put(id, conditions);
      }
      else {
        for (DfaNotNullValue value : conditions) {
          if (value.hardEquals(mySharedInstance)) return value;
        }
      }

      DfaNotNullValue result = new DfaNotNullValue(type, myFactory);
      conditions.add(result);
      return result;
    }
  }

  private PsiType myType;

  private DfaNotNullValue(PsiType myType, DfaValueFactory factory) {
    super(factory);
    this.myType = myType;
  }

  private DfaNotNullValue(DfaValueFactory factory) {
    super(factory);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "@notnull " + myType.getCanonicalText();
  }

  public PsiType getType() {
    return myType;
  }

  private boolean hardEquals(DfaNotNullValue aNotNull) {
    return aNotNull.myType == myType;
  }
}
