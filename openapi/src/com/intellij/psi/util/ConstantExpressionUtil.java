/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.Map;

public class ConstantExpressionUtil {
  public static Object computeCastTo(PsiExpression expression, PsiType castTo) {
    Object value = expression.getManager().getConstantEvaluationHelper().computeConstantExpression(expression, false);
    if(value == null) return null;
    return computeCastTo(value, castTo);
  }

  public static Object computeCastTo(Object operand, PsiType castType) {
    Object value = null;
    if (operand == null || castType == null) return null;
    if (operand instanceof String && castType.equalsToText("java.lang.String")) {
      value = operand;
    }
    else if (operand instanceof Boolean && castType == PsiType.BOOLEAN) {
      value = operand;
    }
    else {
      final String primitiveName = wrapperToPrimitive(operand);
      if (primitiveName == null) return null;
      // identity cast, including (boolean)boolValue
      if (castType.equalsToText(primitiveName)) return operand;
      final int rankFrom = primitiveRank(primitiveName);
      if (rankFrom == 0) return null;
      final int rankTo = primitiveRank(castType.getPresentableText());
      if (rankTo == 0) return null;

      value = caster[rankFrom - 1][rankTo - 1].cast(operand);
    }
    return value;
  }

  private interface Caster {
    Object cast(Object operand);
  }

  private static final Caster[][] caster = new Caster[][]{
    {
      new Caster() {
        public Object cast(Object operand) {
          return operand;
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer(((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long(((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Number) operand).intValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Character) operand).charValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short(((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer(((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long(((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Short) operand).shortValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer(((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long(((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Integer) operand).intValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer((int) ((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long(((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Long) operand).longValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer((int) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long((long) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Float) operand).floatValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return new Byte((byte) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Short((short) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Integer((int) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Long((long) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Double) operand).doubleValue());
        }
      }
    }
  };

  private static final Map wrapperToPrimitive = new HashMap() {
    {
      put(Boolean.class, "boolean");
      put(Byte.class, "byte");
      put(Character.class, "char");
      put(Short.class, "short");
      put(Integer.class, "int");
      put(Long.class, "long");
      put(Float.class, "float");
      put(Double.class, "double");
    }
  };
  private static final TObjectIntHashMap primitiveRank = new TObjectIntHashMap () {
    {
      put("byte", 1);
      put("char", 2);
      put("short", 3);
      put("int", 4);
      put("long", 5);
      put("float", 6);
      put("double", 7);
    }
  };

  private static String wrapperToPrimitive(Object o) {
    String s = (String) wrapperToPrimitive.get(o.getClass());
    return s;
  }

  private static int primitiveRank(String name) {
    return primitiveRank.get(name);
  }

}
