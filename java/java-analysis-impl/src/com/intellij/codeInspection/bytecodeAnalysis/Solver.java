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

import java.util.*;
import static com.intellij.codeInspection.bytecodeAnalysis.IdUtils.*;

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

final class Component<Id> {
  final boolean touched;
  final Set<Id> ids;

  Component(boolean touched, Set<Id> ids) {
    this.touched = touched;
    this.ids = ids;
  }

  Component<Id> remove(Id id) {
    if (ids.contains(id)) {
      HashSet<Id> newIds = new HashSet<Id>(ids);
      newIds.remove(id);
      return new Component<Id>(touched, newIds);
    }
    else {
      return this;
    }
  }

  Component<Id> removeAndTouch(Id id) {
    if (ids.contains(id)) {
      HashSet<Id> newIds = new HashSet<Id>(ids);
      newIds.remove(id);
      return new Component<Id>(true, newIds);
    } else {
      return this;
    }
  }

  boolean isEmpty() {
    return ids.isEmpty();
  }

  boolean isEmptyAndTouched() {
    return ids.isEmpty() && touched;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Component component = (Component) o;
    if (touched != component.touched) return false;
    if (!ids.equals(component.ids)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = (touched ? 1 : 0);
    result = 31 * result + ids.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Component{" + "touched=" + touched + ", ids=" + ids + '}';
  }
}

// component specialized for ints
final class IntIdComponent {
  boolean touched;
  final int[] ids;

  IntIdComponent(boolean touched, int[] ids) {
    this.touched = touched;
    this.ids = ids;
  }

  public void remove(int id) {
    IdUtils.remove(ids, id);
  }

  public boolean isEmptyAndTouched() {
    return touched && IdUtils.isEmpty(ids);
  }

  public boolean isEmpty() {
    return IdUtils.isEmpty(ids);
  }

  public void removeAndTouch(int id) {
    if (IdUtils.remove(ids, id)) {
      touched = true;
    }
  }
}

class IdUtils {
  // absent value
  static final int nullId = -1;

  static boolean contains(int[] ids, int id) {
    for (int i : ids) {
      if (i == id) {
        return true;
      }
    }
    return false;
  }

  static boolean isEmpty(int[] ids) {
    for (int i : ids) {
      if (i != nullId) {
        return false;
      }
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
    for (int i = 0; i < ids.length; i++) {
      if (ids[i] == id) {
        ids[i] = nullId;
        return true;
      }
    }
    return false;
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
      Pending<Id, T> pending = (Pending<Id, T>) r2;
      return new Pending<Id, T>(lattice.join(((Final<Id, T>) r1).value, pending.infinum), pending.delta);
    }
    if (r1 instanceof Pending && r2 instanceof Final) {
      Pending<Id, T> pending = (Pending<Id, T>) r1;
      return new Pending<Id, T>(lattice.join(((Final<Id, T>) r2).value, pending.infinum), pending.delta);
    }
    Pending<Id, T> pending1 = (Pending<Id, T>) r1;
    Pending<Id, T> pending2 = (Pending<Id, T>) r2;
    Set<Component<Id>> delta = new HashSet<Component<Id>>();
    delta.addAll(pending1.delta);
    delta.addAll(pending2.delta);
    return new Pending<Id, T>(lattice.join(pending1.infinum, pending2.infinum), delta);
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
  final T infinum;
  final Set<Component<Id>> delta;

  Pending(T infinum, Set<Component<Id>> delta) {
    this.infinum = infinum;
    this.delta = delta;
  }

  @Override
  public String toString() {
    return "Pending{" + "infinum=" + infinum + ", delta=" + delta + '}';
  }
}

interface IntIdResult {}
// this just wrapper, no need for this really
final class IntIdFinal implements IntIdResult {
  final Value value;
  public IntIdFinal(Value value) {
    this.value = value;
  }
}
final class IntIdPending implements IntIdResult {
  final Value infinum;
  final IntIdComponent[] delta;

  IntIdPending(Value infinum, IntIdComponent[] delta) {
    this.infinum = infinum;
    this.delta = delta;
  }
}
final class IntIdEquation {
  final int id;
  final IntIdResult rhs;

  IntIdEquation(int id, IntIdResult rhs) {
    this.id = id;
    this.rhs = rhs;
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

final class Solver<Id, Val extends Enum<Val>> {
  private final ELattice<Val> lattice;
  private final HashMap<Id, Set<Id>> dependencies = new HashMap<Id, Set<Id>>();
  private final HashMap<Id, Pending<Id, Val>> pending = new HashMap<Id, Pending<Id, Val>>();
  private final Queue<Solution<Id, Val>> moving = new LinkedList<Solution<Id, Val>>();
  private final HashMap<Id, Val> solved = new HashMap<Id, Val>();

  Solver(ELattice<Val> lattice) {
    this.lattice = lattice;
  }

  void addEquation(Equation<Id, Val> equation) {
    if (equation.rhs instanceof Final) {
      Final<Id, Val> finalResult = (Final<Id, Val>) equation.rhs;
      moving.add(new Solution<Id, Val>(equation.id, finalResult.value));
    }
    else if (equation.rhs instanceof Pending) {
      Pending<Id, Val> pendingResult = (Pending<Id, Val>) equation.rhs;
      if (pendingResult.infinum.equals(lattice.top)) {
        moving.add(new Solution<Id, Val>(equation.id, lattice.top));
      }
      else {
        for (Component<Id> component : pendingResult.delta) {
          for (Id trigger : component.ids) {
            Set<Id> set = dependencies.get(trigger);
            if (set == null) {
              set = new HashSet<Id>();
              dependencies.put(trigger, set);
            }
            set.add(equation.id);
          }
        }
        pending.put(equation.id, pendingResult);
      }
    }
  }

  Map<Id, Val> solve() {
    Solution<Id, Val> sol;
    while ((sol = moving.poll()) != null) {
      solved.put(sol.id, sol.value);
      Set<Id> dIds = dependencies.remove(sol.id);
      if (dIds != null) {
        for (Id dId : dIds) {
          Pending<Id, Val> pend = pending.remove(dId);
          if (pend != null) {
            Result<Id, Val> pend1 = substitute(pend, sol.id, sol.value);
            if (pend1 instanceof Final) {
              Final<Id, Val> fi = (Final<Id, Val>) pend1;
              moving.add(new Solution<Id, Val>(dId, fi.value));
            }
            else {
              pending.put(dId, (Pending<Id, Val>) pend1);
            }
          }
        }
      }
    }
    pending.clear();
    return solved;
  }

  Result<Id, Val> substitute(Pending<Id, Val> pending, Id id, Val value) {
    if (value.equals(lattice.bot)) {
      HashSet<Component<Id>> delta = new HashSet<Component<Id>>();
      for (Component<Id> component : pending.delta) {
        if (!component.ids.contains(id)) {
          delta.add(component);
        }
      }
      if (delta.isEmpty()) {
        return new Final<Id, Val>(pending.infinum);
      }
      else {
        return new Pending<Id, Val>(pending.infinum, delta);
      }
    }
    else if (value.equals(lattice.top)) {
      HashSet<Component<Id>> delta = new HashSet<Component<Id>>();
      for (Component<Id> component : pending.delta) {
        Component<Id> component1 = component.remove(id);
        if (!component1.isEmptyAndTouched()) {
          if (component1.isEmpty()) {
            return new Final<Id, Val>(lattice.top);
          } else {
            delta.add(component1);
          }
        }
      }
      if (delta.isEmpty()) {
        return new Final<Id, Val>(pending.infinum);
      }
      else {
        return new Pending<Id, Val>(pending.infinum, delta);
      }
    }
    else {
      Val infinum = lattice.join(pending.infinum, value);
      if (infinum == lattice.top) {
        return new Final<Id, Val>(lattice.top);
      }
      HashSet<Component<Id>> delta = new HashSet<Component<Id>>();
      for (Component<Id> component : pending.delta) {
        Component<Id> component1 = component.removeAndTouch(id);
        if (!component1.isEmpty()) {
          delta.add(component1);
        }
      }
      if (delta.isEmpty()) {
        return new Final<Id, Val>(infinum);
      }
      else {
        return new Pending<Id, Val>(infinum, delta);
      }
    }
  }
}

final class IntIdSolver {

  private final ELattice<Value> lattice;
  private final IntToIntSetMap dependencies = new IntToIntSetMap(10000, 0.5f);
  private final TIntObjectHashMap<IntIdPending> pending = new TIntObjectHashMap<IntIdPending>();
  private final TIntObjectHashMap<Value> solved = new TIntObjectHashMap<Value>();
  private final IntStack moving = new IntStack();

  IntIdSolver(ELattice<Value> lattice) {
    this.lattice = lattice;
  }

  void addEquation(IntIdEquation equation) {
    IntIdResult rhs = equation.rhs;
    if (rhs instanceof IntIdFinal) {
      solved.put(equation.id, ((IntIdFinal) rhs).value);
      moving.push(equation.id);
    } else if (rhs instanceof IntIdPending) {
      IntIdPending pendResult = (IntIdPending)rhs;
      if (pendResult.infinum == lattice.top) {
        solved.put(equation.id, lattice.top);
        moving.push(equation.id);
      } else {
        for (IntIdComponent component : pendResult.delta) {
          for (int trigger : component.ids) {
            dependencies.addOccurence(trigger, equation.id);
          }
        }
        pending.put(equation.id, pendResult);
      }
    }
  }

  // TODO - to int
  TIntObjectHashMap<Value> solve() {
    while (!moving.empty()) {
      int id = moving.pop();
      Value value = solved.get(id);
      int[] dIds = dependencies.get(id);

      for (int dId : dIds) {
        IntIdPending pend = pending.remove(dId);
        if (pend != null) {
          IntIdResult pend1 = substitute(pend, id, value);
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
    pending.clear();
    return solved;
  }

  // substitute id -> value into pending
  IntIdResult substitute(IntIdPending pending, int id, Value value) {
    if (value == lattice.bot) {
      // remove components (products) with bottom
      ArrayList<IntIdComponent> delta = new ArrayList<IntIdComponent>();
      for (IntIdComponent component : pending.delta) {
        if (!contains(component.ids, id)) {
          delta.add(component);
        }
      }
      if (delta.isEmpty()) {
        return new IntIdFinal(pending.infinum);
      }
      else {
        return new IntIdPending(pending.infinum, toArray(delta));
      }
    }
    else if (value.equals(lattice.top)) {
      ArrayList<IntIdComponent> delta = new ArrayList<IntIdComponent>();
      // remove top from components
      for (IntIdComponent component : pending.delta) {
        component.remove(id);
        if (!component.isEmptyAndTouched()) {
          if (component.isEmpty()) {
            return new IntIdFinal(lattice.top);
          } else {
            delta.add(component);
          }
        }
      }
      if (delta.isEmpty()) {
        return new IntIdFinal(pending.infinum);
      }
      else {
        return new IntIdPending(pending.infinum, toArray(delta));
      }
    }
    else {
      Value infinum = lattice.join(pending.infinum, value);
      if (infinum == lattice.top) {
        return new IntIdFinal(lattice.top);
      }
      ArrayList<IntIdComponent> delta = new ArrayList<IntIdComponent>();
      for (IntIdComponent component : pending.delta) {
        component.removeAndTouch(id);
        if (!component.isEmpty()) {
          delta.add(component);
        }
      }
      if (delta.isEmpty()) {
        return new IntIdFinal(infinum);
      }
      else {
        return new IntIdPending(infinum, toArray(delta));
      }
    }
  }
}