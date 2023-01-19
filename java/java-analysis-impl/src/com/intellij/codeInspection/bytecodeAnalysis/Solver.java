// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * For lattice, equations and solver description, see http://pat.keldysh.ru/~ilya/faba.pdf (in Russian)
 */
final class ELattice<T extends Enum<T>> {
  final T bot;
  final T top;

  ELattice(T bot, T top) {
    this.bot = bot;
    this.top = top;
  }

  T join(T x, T y) {
    if (x == bot) return y;
    if (y == bot) return x;
    if (x == y) return x;
    return top;
  }

  T meet(T x, T y) {
    if (x == top) return y;
    if (y == top) return x;
    if (x == y) return x;
    return bot;
  }
}


class ResultUtil {
  private static final EKey[] EMPTY_PRODUCT = new EKey[0];
  private final ELattice<Value> lattice;
  final Value top;
  final Value bottom;

  ResultUtil(ELattice<Value> lattice) {
    this.lattice = lattice;
    top = lattice.top;
    bottom = lattice.bot;
  }

  Result join(Result r1, Result r2) {
    Result result = checkFinal(r1, r2);
    if (result != null) return result;
    result = checkFinal(r1, r2);
    if (result != null) return result;
    if (r1 instanceof Value && r2 instanceof Value) {
      return lattice.join((Value)r1, (Value)r2);
    }
    if (r1 instanceof Value && r2 instanceof Pending) {
      return addSingle((Pending)r2, (Value)r1);
    }
    if (r1 instanceof Pending && r2 instanceof Value) {
      return addSingle((Pending)r1, (Value)r2);
    }
    assert r1 instanceof Pending && r2 instanceof Pending;
    Pending pending1 = (Pending)r1;
    Pending pending2 = (Pending)r2;
    List<Component> left = Arrays.asList(pending1.delta);
    List<Component> right = Arrays.asList(pending2.delta);
    if (left.containsAll(right)) return pending1;
    if (right.containsAll(left)) return pending2;
    Set<Component> sum = new HashSet<>();
    sum.addAll(left);
    sum.addAll(right);
    return new Pending(DfaUtil.upwardsAntichain(sum, (l, r) -> r.isSuperStateOf(l)));
  }

  @Nullable
  private Result checkFinal(Result r1, Result r2) {
    if (r1 == top) return r1;
    if (r1 == bottom) return r2;
    return null;
  }

  @NotNull
  private Result addSingle(Pending pending, Value value) {
    for (int i = 0; i < pending.delta.length; i++) {
      Component component = pending.delta[i];
      if (component.ids.length == 0) {
        Value join = lattice.join(component.value, value);
        if (join == top) {
          return top;
        }
        else if (join == component.value) {
          return pending;
        }
        else {
          Component[] components = pending.delta.clone();
          components[i] = new Component(join, EMPTY_PRODUCT);
          return new Pending(components);
        }
      }
    }
    return new Pending(ArrayUtil.append(pending.delta, new Component(value, EMPTY_PRODUCT)));
  }
}

final class CoreHKey {
  final @NotNull MemberDescriptor myMethod;
  final int dirKey;

  CoreHKey(@NotNull MemberDescriptor method, int dirKey) {
    this.myMethod = method;
    this.dirKey = dirKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CoreHKey coreHKey = (CoreHKey)o;
    return dirKey == coreHKey.dirKey && myMethod.equals(coreHKey.myMethod);
  }

  @Override
  public int hashCode() {
    return 31 * myMethod.hashCode() + dirKey;
  }

  @Override
  public String toString() {
    return "CoreHKey [" + myMethod + "|" + Direction.fromInt(dirKey) + "]";
  }
}

final class Solver {
  private final ELattice<Value> lattice;
  private final HashMap<EKey, HashSet<EKey>> dependencies = new HashMap<>();
  private final HashMap<EKey, Pending> pending = new HashMap<>();
  private final HashMap<EKey, Value> solved = new HashMap<>();
  private final Stack<EKey> moving = new Stack<>();

  private final ResultUtil resultUtil;
  private final HashMap<CoreHKey, Equation> equations = new HashMap<>();
  private final Value unstableValue;

  Solver(ELattice<Value> lattice, Value unstableValue) {
    this.lattice = lattice;
    this.unstableValue = unstableValue;
    resultUtil = new ResultUtil(lattice);
  }

  Result getUnknownResult() {
    return unstableValue;
  }

  void addEquation(Equation equation) {
    EKey key = equation.key;
    CoreHKey coreKey = new CoreHKey(key.member, key.dirKey);

    Equation previousEquation = equations.get(coreKey);
    if (previousEquation == null) {
      equations.put(coreKey, equation);
    }
    else {
      EKey joinKey = new EKey(coreKey.myMethod, coreKey.dirKey, equation.key.stable && previousEquation.key.stable, false);
      Result joinResult = resultUtil.join(equation.result, previousEquation.result);
      Equation joinEquation = new Equation(joinKey, joinResult);
      equations.put(coreKey, joinEquation);
    }
  }

  void queueEquation(Equation equation) {
    Result rhs = equation.result;
    if (rhs instanceof Value) {
      solved.put(equation.key, (Value)rhs);
      moving.push(equation.key);
    }
    else if (rhs instanceof Pending) {
      Pending pendResult = ((Pending)rhs).copy();
      Result norm = normalize(pendResult.delta);
      if (norm instanceof Value) {
        solved.put(equation.key, (Value)norm);
        moving.push(equation.key);
      }
      else {
        Pending pendResult1 = ((Pending)rhs).copy();
        for (Component component : pendResult1.delta) {
          for (EKey trigger : component.ids) {
            HashSet<EKey> set = dependencies.get(trigger);
            if (set == null) {
              set = new HashSet<>();
              dependencies.put(trigger, set);
            }
            set.add(equation.key);
          }
        }
        pending.put(equation.key, pendResult1);
      }
    }
  }

  Value negate(Value value) {
    return switch (value) {
      case True -> Value.False;
      case False -> Value.True;
      default -> value;
    };
  }

  Map<EKey, Value> solve() {
    for (Equation equation : equations.values()) {
      queueEquation(equation);
    }
    while (!moving.empty()) {
      EKey id = moving.pop();
      Value value = solved.get(id);

      EKey[] initialPIds = id.stable ? new EKey[]{id, id.invertStability()} : new EKey[]{id.invertStability(), id};
      Value[] initialPVals = id.stable ? new Value[]{value, value} : new Value[]{value, unstableValue};

      EKey[] pIds = new EKey[]{initialPIds[0], initialPIds[1], initialPIds[0].negate(), initialPIds[1].negate()};
      Value[] pVals = new Value[]{initialPVals[0], initialPVals[1], negate(initialPVals[0]), negate(initialPVals[1])};

      for (int i = 0; i < pIds.length; i++) {
        EKey pId = pIds[i];
        Value pVal = pVals[i];
        HashSet<EKey> dIds = dependencies.get(pId);
        if (dIds == null) {
          continue;
        }
        for (EKey dId : dIds) {
          Pending pend = pending.remove(dId);
          if (pend != null) {
            Result pend1 = substitute(pend, pId, pVal);
            if (pend1 instanceof Value) {
              solved.put(dId, (Value)pend1);
              moving.push(dId);
            }
            else {
              pending.put(dId, (Pending)pend1);
            }
          }
        }
      }
    }
    pending.clear();
    return solved;
  }

  // substitute id -> value into pending
  Result substitute(@NotNull Pending pending, @NotNull EKey id, @NotNull Value value) {
    Component[] sum = pending.delta;
    for (Component intIdComponent : sum) {
      if (intIdComponent.remove(id)) {
        intIdComponent.value = lattice.meet(intIdComponent.value, value);
      }
    }
    return normalize(sum);
  }

  @NotNull
  Result normalize(Component @NotNull [] sum) {
    Value acc = lattice.bot;
    boolean computableNow = true;
    for (Component prod : sum) {
      if (prod.isEmpty() || prod.value == lattice.bot) {
        acc = lattice.join(acc, prod.value);
      }
      else {
        computableNow = false;
      }
    }
    return (acc == lattice.top || computableNow) ? acc : new Pending(sum);
  }
}