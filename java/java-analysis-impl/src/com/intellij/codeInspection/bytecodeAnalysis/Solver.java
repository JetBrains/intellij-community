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
    return "Component{" +
           "touched=" + touched +
           ", ids=" + ids +
           '}';
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
    return "Final{" +
           "value=" + value +
           '}';
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
    return "Pending{" +
           "infinum=" + infinum +
           ", delta=" + delta +
           '}';
  }
}

interface IntIdResult<T> {}
// this just wrapper, no need for this really
final class IntIdFinal<T> implements IntIdResult<T> {
  final T value;
  public IntIdFinal(T value) {
    this.value = value;
  }
}
final class IntIdPending<T> implements IntIdResult<T> {
  final T infinum;
  final IntIdComponent[] delta;

  IntIdPending(T infinum, IntIdComponent[] delta) {
    this.infinum = infinum;
    this.delta = delta;
  }
}
final class IntIdEquation<T> {
  final int id;
  final IntIdResult<T> rhs;

  IntIdEquation(int id, IntIdResult<T> rhs) {
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
    return "Equation{" +
           "id=" + id +
           ", rhs=" + rhs +
           '}';
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

  // we can make it in place
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
  ArrayList<IntIdEquation> equations = new ArrayList<IntIdEquation>();
  void addEquation(IntIdEquation equation) {
    equations.add(equation);
  }
}