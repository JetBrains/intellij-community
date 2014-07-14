/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.util.containers.IntStack;
import com.intellij.util.containers.IntToIntSetMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class ELattice<T extends Enum<T>> {
  final T bot;
  final T top;

  ELattice(T bot, T top) {
    this.bot = bot;
    this.top = top;
  }

  final T join(T x, T y) {
    if (x == bot) return y;
    if (y == bot) return x;
    if (x == y) return x;
    return top;
  }

  final T meet(T x, T y) {
    if (x == top) return y;
    if (y == top) return x;
    if (x == y) return x;
    return bot;
  }
}

// component specialized for ints
final class IntIdComponent {
  Value value;
  final int[] ids;

  IntIdComponent(Value value,  int[] ids) {
    this.value = value;
    this.ids = ids;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntIdComponent that = (IntIdComponent)o;

    if (!Arrays.equals(ids, that.ids)) return false;
    if (value != that.value) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return value.ordinal() + Arrays.hashCode(ids);
  }

  public boolean remove(int id) {
    return IdUtils.remove(ids, id);
  }

  public boolean isEmpty() {
    return IdUtils.isEmpty(ids);
  }

  IntIdComponent copy() {
    return new IntIdComponent(value, ids.clone());
  }
}

class IdUtils {
  // removed value
  static final int nullId = 0;

  static boolean contains(int[] ids, int id) {
    for (int id1 : ids) {
      if (id1 == id) return true;
    }

    return false;
  }

  static boolean isEmpty(int[] ids) {
    for (int id : ids) {
      if (id != nullId) return false;
    }
    return true;
  }

  static IntIdComponent[] toArray(Collection<IntIdComponent> set) {
    IntIdComponent[] result = new IntIdComponent[set.size()];
    int i = 0;
    for (IntIdComponent intIdComponent : set) {
      result[i] = intIdComponent;
      i++;
    }

    return result;
  }

  static boolean remove(int[] ids, int id) {
    boolean removed = false;
    for (int i = 0; i < ids.length; i++) {
      if (ids[i] == id) {
        ids[i] = nullId;
        removed = true;
      }
    }
    return removed;
  }
}

class ResultUtil<Id, T extends Enum<T>> {
  private final ELattice<T> lattice;
  final T top;
  ResultUtil(ELattice<T> lattice) {
    this.lattice = lattice;
    top = lattice.top;
  }

  Result<Id, T> join(Result<Id, T> r1, Result<Id, T> r2) {
    if (r1 instanceof Final && ((Final) r1).value == top) {
      return r1;
    }
    if (r2 instanceof Final && ((Final) r2).value == top) {
      return r2;
    }
    if (r1 instanceof Final && r2 instanceof Final) {
      return new Final<Id, T>(lattice.join(((Final<?, T>) r1).value, ((Final<?, T>) r2).value));
    }
    if (r1 instanceof Final && r2 instanceof Pending) {
      Final<?, T> f1 = (Final<?, T>)r1;
      Pending<Id, T> pending = (Pending<Id, T>) r2;
      Set<Product<Id, T>> sum1 = new HashSet<Product<Id, T>>(pending.sum);
      sum1.add(new Product<Id, T>(f1.value, Collections.<Id>emptySet()));
      return new Pending<Id, T>(sum1);
    }
    if (r1 instanceof Pending && r2 instanceof Final) {
      Final<?, T> f2 = (Final<?, T>)r2;
      Pending<Id, T> pending = (Pending<Id, T>) r1;
      Set<Product<Id, T>> sum1 = new HashSet<Product<Id, T>>(pending.sum);
      sum1.add(new Product<Id, T>(f2.value, Collections.<Id>emptySet()));
      return new Pending<Id, T>(sum1);
    }
    Pending<Id, T> pending1 = (Pending<Id, T>) r1;
    Pending<Id, T> pending2 = (Pending<Id, T>) r2;
    Set<Product<Id, T>> sum = new HashSet<Product<Id, T>>();
    sum.addAll(pending1.sum);
    sum.addAll(pending2.sum);
    return new Pending<Id, T>(sum);
  }
}

final class Product<K, V> {
  @NotNull final V value;
  @NotNull final Set<K> ids;

  Product(@NotNull V value, @NotNull Set<K> ids) {
    this.value = value;
    this.ids = ids;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Product product = (Product)o;

    if (!ids.equals(product.ids)) return false;
    if (!value.equals(product.value)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + ids.hashCode();
    return result;
  }
}

interface Result<Id, T> {}
final class Final<Id, T> implements Result<Id, T> {
  final T value;
  Final(T value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "Final{" + "value=" + value + '}';
  }
}

final class Pending<Id, T> implements Result<Id, T> {
  final Set<Product<Id, T>> sum;

  Pending(Set<Product<Id, T>> sum) {
    this.sum = sum;
  }

}

interface IntIdResult {}
// this just wrapper, no need for this really
final class IntIdFinal implements IntIdResult {
  final Value value;
  public IntIdFinal(Value value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntIdFinal that = (IntIdFinal)o;

    if (value != that.value) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return value.ordinal();
  }

  @Override
  public String toString() {
    return super.toString();
  }
}

final class IntIdPending implements IntIdResult {
  final IntIdComponent[] delta;

  IntIdPending(IntIdComponent[] delta) {
    this.delta = delta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IntIdPending)) return false;
    IntIdPending pending = (IntIdPending)o;
    return !Arrays.equals(delta, pending.delta);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(delta);
  }

  IntIdPending copy() {
    IntIdComponent[] delta1 = new IntIdComponent[delta.length];
    for (int i = 0; i < delta.length; i++) {
      delta1[i] = delta[i].copy();
    }
    return new IntIdPending(delta1);
  }
}

final class IntIdEquation {
  final int id;
  final IntIdResult rhs;

  IntIdEquation(int id, IntIdResult rhs) {
    this.id = id;
    this.rhs = rhs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IntIdEquation)) return false;

    IntIdEquation equation = (IntIdEquation)o;

    if (id != equation.id) return false;
    if (!rhs.equals(equation.rhs)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + rhs.hashCode();
    return result;
  }
}

final class Solution<Id, Val> {
  final Id id;
  final Val value;

  Solution(Id id, Val value) {
    this.id = id;
    this.value = value;
  }
}

final class Equation<Id, T> {
  final Id id;
  final Result<Id, T> rhs;

  Equation(Id id, Result<Id, T> rhs) {
    this.id = id;
    this.rhs = rhs;
  }

  @Override
  public String toString() {
    return "Equation{" + "id=" + id + ", rhs=" + rhs + '}';
  }
}

final class IntIdSolver {

  private int size = 0;
  private final ELattice<Value> lattice;
  private final IntToIntSetMap dependencies = new IntToIntSetMap(10000, 0.5f);
  private final TIntObjectHashMap<IntIdPending> pending = new TIntObjectHashMap<IntIdPending>();
  private final TIntObjectHashMap<Value> solved = new TIntObjectHashMap<Value>();
  private final IntStack moving = new IntStack();

  int getSize() {
    return size;
  }

  IntIdSolver(ELattice<Value> lattice) {
    this.lattice = lattice;
  }

  void addEquation(IntIdEquation equation) {
    size ++;
    IntIdResult rhs = equation.rhs;
    if (rhs instanceof IntIdFinal) {
      solved.put(equation.id, ((IntIdFinal) rhs).value);
      moving.push(equation.id);
    } else if (rhs instanceof IntIdPending) {
      IntIdPending pendResult = ((IntIdPending)rhs).copy();
      IntIdResult norm = normalize(pendResult.delta);
      if (norm instanceof IntIdFinal) {
        solved.put(equation.id, ((IntIdFinal) norm).value);
        moving.push(equation.id);
      }
      else {
        IntIdPending pendResult1 = ((IntIdPending)rhs).copy();
        for (IntIdComponent component : pendResult1.delta) {
          for (int trigger : component.ids) {
            dependencies.addOccurence(trigger, equation.id);
          }
          pending.put(equation.id, pendResult1);
        }
      }
    }
  }

  TIntObjectHashMap<Value> solve() {
    while (!moving.empty()) {
      int id = moving.pop();
      Value value = solved.get(id);

      boolean stable = id > 0;
      int[] pIds  = stable ? new int[]{id, -id} : new int[]{-id, id};
      Value[] pVals = stable ? new Value[]{value, value} : new Value[]{value, lattice.top};

      for (int i = 0; i < pIds.length; i++) {
        int pId = pIds[i];
        Value pVal = pVals[i];
        // todo - remove
        int[] dIds = dependencies.get(pId);
        for (int dId : dIds) {
          IntIdPending pend = pending.remove(dId);
          if (pend != null) {
            IntIdResult pend1 = substitute(pend, pId, pVal);
            if (pend1 instanceof IntIdFinal) {
              IntIdFinal fi = (IntIdFinal)pend1;
              solved.put(dId, fi.value);
              moving.push(dId);
            }
            else {
              pending.put(dId, (IntIdPending)pend1);
            }
          }
        }
      }
    }
    pending.clear();
    return solved;
  }

  // substitute id -> value into pending
  IntIdResult substitute(IntIdPending pending, int id, Value value) {
    IntIdComponent[] sum = pending.delta;
    for (IntIdComponent intIdComponent : sum) {
      if (intIdComponent.remove(id)) {
        intIdComponent.value = lattice.meet(intIdComponent.value, value);
      }
    }
    return normalize(sum);
  }

  IntIdResult normalize(IntIdComponent[] sum) {
    Value acc = lattice.bot;
    boolean computableNow = true;
    for (IntIdComponent prod : sum) {
      if (prod.isEmpty() || prod.value == lattice.bot) {
        acc = lattice.join(acc, prod.value);
      } else {
        computableNow = false;
      }
    }
    return (acc == lattice.top || computableNow) ? new IntIdFinal(acc) : new IntIdPending(sum);
  }

}
