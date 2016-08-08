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
package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.Util;
import com.intellij.refactoring.typeCook.deductive.PsiExtendedTypeVisitor;
import com.intellij.refactoring.typeCook.deductive.builder.Constraint;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.Subtype;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectIntHashMap;

import java.util.*;

/**
 * @author db
 */
public class ResolverTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree");

  private ResolverTree[] mySons = new ResolverTree[0];
  private final BindingFactory myBindingFactory;
  private Binding myCurrentBinding;
  private final SolutionHolder mySolutions;
  private final Project myProject;
  private final TObjectIntHashMap<PsiTypeVariable> myBindingDegree; //How many times this type variable is bound in the system
  private final Settings mySettings;
  private boolean mySolutionFound;

  private Set<Constraint> myConstraints;

  public ResolverTree(final ReductionSystem system) {
    myBindingFactory = new BindingFactory(system);
    mySolutions = new SolutionHolder();
    myCurrentBinding = myBindingFactory.create();
    myConstraints = system.getConstraints();
    myProject = system.getProject();
    myBindingDegree = calculateDegree();
    mySettings = system.getSettings();

    reduceCyclicVariables();
  }

  private ResolverTree(final ResolverTree parent, final Set<Constraint> constraints, final Binding binding) {
    myBindingFactory = parent.myBindingFactory;
    myCurrentBinding = binding;
    mySolutions = parent.mySolutions;
    myConstraints = constraints;
    myProject = parent.myProject;
    myBindingDegree = calculateDegree();
    mySettings = parent.mySettings;
  }

  private static class PsiTypeVarCollector extends PsiExtendedTypeVisitor {
    final Set<PsiTypeVariable> mySet = new HashSet<>();

    @Override
    public Object visitTypeVariable(final PsiTypeVariable var) {
      mySet.add(var);

      return null;
    }

    public Set<PsiTypeVariable> getSet(final PsiType type) {
      type.accept(this);
      return mySet;
    }
  }

  private boolean isBoundElseWhere(final PsiTypeVariable var) {
    return myBindingDegree.get(var) != 1;
  }

  private boolean canBePruned(final Binding b) {
    if (mySettings.exhaustive()) return false;
    for (final PsiTypeVariable var : b.getBoundVariables()) {
      final PsiType type = b.apply(var);

      if (!(type instanceof PsiTypeVariable) && isBoundElseWhere(var)) {
        return false;
      }
    }

    return true;
  }

  private TObjectIntHashMap<PsiTypeVariable> calculateDegree() {
    final TObjectIntHashMap<PsiTypeVariable> result = new TObjectIntHashMap<>();

    for (final Constraint constr : myConstraints) {
      final PsiTypeVarCollector collector = new PsiTypeVarCollector();

      setDegree(collector.getSet(constr.getRight()), result);
    }

    return result;
  }

  private void setDegree(final Set<PsiTypeVariable> set, TObjectIntHashMap<PsiTypeVariable> result) {
    for (final PsiTypeVariable var : set) {
      result.increment(var);
    }
  }

  private Set<Constraint> apply(final Binding b) {
    final Set<Constraint> result = new HashSet<>();

    for (final Constraint constr : myConstraints) {
      result.add(constr.apply(b));
    }

    return result;
  }

  private Set<Constraint> apply(final Binding b, final Set<Constraint> additional) {
    final Set<Constraint> result = new HashSet<>();

    for (final Constraint constr : myConstraints) {
      result.add(constr.apply(b));
    }

    for (final Constraint constr : additional) {
      result.add(constr.apply(b));
    }

    return result;
  }

  private ResolverTree applyRule(final Binding b) {
    final Binding newBinding = b != null ? myCurrentBinding.compose(b) : null;

    return newBinding == null ? null : new ResolverTree(this, apply(b), newBinding);
  }

  private ResolverTree applyRule(final Binding b, final Set<Constraint> additional) {
    final Binding newBinding = b != null ? myCurrentBinding.compose(b) : null;

    return newBinding == null ? null : new ResolverTree(this, apply(b, additional), newBinding);
  }

  private void reduceCyclicVariables() {
    final Set<PsiTypeVariable> nodes = new HashSet<>();
    final Set<Constraint> candidates = new HashSet<>();

    final Map<PsiTypeVariable, Set<PsiTypeVariable>> ins = new HashMap<>();
    final Map<PsiTypeVariable, Set<PsiTypeVariable>> outs = new HashMap<>();

    for (final Constraint constraint : myConstraints) {
      final PsiType left = constraint.getLeft();
      final PsiType right = constraint.getRight();

      if (left instanceof PsiTypeVariable && right instanceof PsiTypeVariable) {
        final PsiTypeVariable leftVar = (PsiTypeVariable)left;
        final PsiTypeVariable rightVar = (PsiTypeVariable)right;

        candidates.add(constraint);

        nodes.add(leftVar);
        nodes.add(rightVar);

        Set<PsiTypeVariable> in = ins.get(leftVar);
        Set<PsiTypeVariable> out = outs.get(rightVar);

        if (in == null) {
          final Set<PsiTypeVariable> newIn = new HashSet<>();

          newIn.add(rightVar);

          ins.put(leftVar, newIn);
        }
        else {
          in.add(rightVar);
        }

        if (out == null) {
          final Set<PsiTypeVariable> newOut = new HashSet<>();

          newOut.add(leftVar);

          outs.put(rightVar, newOut);
        }
        else {
          out.add(leftVar);
        }
      }
    }

    final DFSTBuilder<PsiTypeVariable> dfstBuilder = new DFSTBuilder<>(new Graph<PsiTypeVariable>() {
      @Override
      public Collection<PsiTypeVariable> getNodes() {
        return nodes;
      }

      @Override
      public Iterator<PsiTypeVariable> getIn(final PsiTypeVariable n) {
        final Set<PsiTypeVariable> in = ins.get(n);

        if (in == null) {
          return EmptyIterator.getInstance();
        }

        return in.iterator();
      }

      @Override
      public Iterator<PsiTypeVariable> getOut(final PsiTypeVariable n) {
        final Set<PsiTypeVariable> out = outs.get(n);

        if (out == null) {
          return EmptyIterator.getInstance();
        }

        return out.iterator();
      }
    });

    final TIntArrayList sccs = dfstBuilder.getSCCs();
    final Map<PsiTypeVariable, Integer> index = new HashMap<>();

    sccs.forEach(new TIntProcedure() {
      int myTNumber;

      @Override
      public boolean execute(int size) {
        for (int j = 0; j < size; j++) {
          index.put(dfstBuilder.getNodeByTNumber(myTNumber + j), myTNumber);
        }
        myTNumber += size;
        return true;
      }
    });

    for (final Constraint constraint : candidates) {
      if (index.get(constraint.getLeft()).equals(index.get(constraint.getRight()))) {
        myConstraints.remove(constraint);
      }
    }

    Binding binding = myBindingFactory.create();

    for (final PsiTypeVariable fromVar : index.keySet()) {
      final PsiTypeVariable toVar = dfstBuilder.getNodeByNNumber(index.get(fromVar).intValue());

      if (!fromVar.equals(toVar)) {
        binding = binding.compose(myBindingFactory.create(fromVar, toVar));

        if (binding == null) {
          break;
        }
      }
    }

    if (binding != null && binding.nonEmpty()) {
      myCurrentBinding = myCurrentBinding.compose(binding);
      myConstraints = apply(binding);
    }
  }

  private void reduceTypeType(final Constraint constr) {
    final PsiType left = constr.getLeft();
    final PsiType right = constr.getRight();
    final Set<Constraint> addendumRise = new HashSet<>();
    final Set<Constraint> addendumSink = new HashSet<>();
    final Set<Constraint> addendumWcrd = new HashSet<>();

    int numSons = 0;
    Binding riseBinding = myBindingFactory.rise(left, right, addendumRise);
    if (riseBinding != null) numSons++;
    Binding sinkBinding = myBindingFactory.sink(left, right, addendumSink);
    if (sinkBinding != null) numSons++;
    Binding wcrdBinding = mySettings.cookToWildcards() ? myBindingFactory.riseWithWildcard(left, right, addendumWcrd) : null;
    if (wcrdBinding != null) numSons++;
    Binding omitBinding = null;

    if (mySettings.exhaustive()) {
      final PsiClassType.ClassResolveResult rightResult = Util.resolveType(right);
      final PsiClassType.ClassResolveResult leftResult = Util.resolveType(left);

      final PsiClass rightClass = rightResult.getElement();
      final PsiClass leftClass = leftResult.getElement();

      if (rightClass != null && leftClass != null && rightClass.getManager().areElementsEquivalent(rightClass, leftClass)) {
        if (PsiUtil.typeParametersIterator(rightClass).hasNext()) {
          omitBinding = myBindingFactory.create();
          numSons++;
          for (PsiType type : rightResult.getSubstitutor().getSubstitutionMap().values()) {
            if (! (type instanceof Bottom)) {
              numSons--;
              omitBinding = null;
              break;
            }
          }
        }
      }
    }

    if (numSons == 0) return;

    if ((riseBinding != null && sinkBinding != null && riseBinding.equals(sinkBinding)) || canBePruned(riseBinding)) {
      numSons--;
      sinkBinding = null;
    }

    if (riseBinding != null && wcrdBinding != null && riseBinding.equals(wcrdBinding)) {
      numSons--;
      wcrdBinding = null;
    }

    myConstraints.remove(constr);

    mySons = new ResolverTree[numSons];

    int n = 0;

    if (riseBinding != null) {
      mySons[n++] = applyRule(riseBinding, addendumRise);
    }

    if (wcrdBinding != null) {
      mySons[n++] = applyRule(wcrdBinding, addendumWcrd);
    }

    if (omitBinding != null) {
      mySons[n++] = applyRule(omitBinding, addendumWcrd);
    }

    if (sinkBinding != null) {
      mySons[n++] = applyRule(sinkBinding, addendumSink);
    }
  }

  private void fillTypeRange(final PsiType lowerBound,
                             final PsiType upperBound,
                             final Set<PsiType> holder) {
    if (lowerBound instanceof PsiClassType && upperBound instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resultLower = ((PsiClassType)lowerBound).resolveGenerics();
      final PsiClassType.ClassResolveResult resultUpper = ((PsiClassType)upperBound).resolveGenerics();

      final PsiClass lowerClass = resultLower.getElement();
      final PsiClass upperClass = resultUpper.getElement();

      if (lowerClass != null && upperClass != null && !lowerClass.equals(upperClass)) {
        final PsiSubstitutor upperSubst = resultUpper.getSubstitutor();
        final PsiClass[] parents = upperClass.getSupers();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

        for (final PsiClass parent : parents) {
          final PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(parent, upperClass, upperSubst);
          if (superSubstitutor != null) {
            final PsiClassType type = factory.createType(parent, superSubstitutor);
            holder.add(type);
            fillTypeRange(lowerBound, type, holder);
          }
        }
      }
    }
    else if (lowerBound instanceof PsiArrayType && upperBound instanceof PsiArrayType) {
      fillTypeRange(((PsiArrayType)lowerBound).getComponentType(), ((PsiArrayType)upperBound).getComponentType(), holder);
    }
  }

  private PsiType[] getTypeRange(final PsiType lowerBound, final PsiType upperBound) {
    Set<PsiType> range = new HashSet<>();

    range.add(lowerBound);
    range.add(upperBound);

    fillTypeRange(lowerBound, upperBound, range);

    return range.toArray(PsiType.createArray(range.size()));
  }

  private void reduceInterval(final Constraint left, final Constraint right) {
    final PsiType leftType = left.getLeft();
    final PsiType rightType = right.getRight();
    final PsiTypeVariable var = (PsiTypeVariable)left.getRight();

    if (leftType.equals(rightType)) {
      final Binding binding = myBindingFactory.create(var, leftType);

      myConstraints.remove(left);
      myConstraints.remove(right);

      mySons = new ResolverTree[]{applyRule(binding)};

      return;
    }

    Binding riseBinding = myBindingFactory.rise(leftType, rightType, null);
    Binding sinkBinding = myBindingFactory.sink(leftType, rightType, null);

    int indicator = (riseBinding == null ? 0 : 1) + (sinkBinding == null ? 0 : 1);

    if (indicator == 0) {
      return;
    }
    else if ((indicator == 2 && riseBinding.equals(sinkBinding)) || canBePruned(riseBinding)) {
      indicator = 1;
      sinkBinding = null;
    }

    PsiType[] riseRange = PsiType.EMPTY_ARRAY;
    PsiType[] sinkRange = PsiType.EMPTY_ARRAY;

    if (riseBinding != null) {
      riseRange = getTypeRange(riseBinding.apply(rightType), riseBinding.apply(leftType));
    }

    if (sinkBinding != null) {
      sinkRange = getTypeRange(sinkBinding.apply(rightType), sinkBinding.apply(leftType));
    }

    if (riseRange.length + sinkRange.length > 0) {
      myConstraints.remove(left);
      myConstraints.remove(right);
    }

    mySons = new ResolverTree[riseRange.length + sinkRange.length];

    for (int i = 0; i < riseRange.length; i++) {
      final PsiType type = riseRange[i];

      mySons[i] = applyRule(riseBinding.compose(myBindingFactory.create(var, type)));
    }

    for (int i = 0; i < sinkRange.length; i++) {
      final PsiType type = sinkRange[i];

      mySons[i + riseRange.length] = applyRule(sinkBinding.compose(myBindingFactory.create(var, type)));
    }
  }

  private void reduce() {
    if (myConstraints.isEmpty()) {
      return;
    }

    if (myCurrentBinding.isCyclic()) {
      reduceCyclicVariables();
    }

    final Map<PsiTypeVariable, Constraint> myTypeVarConstraints = new HashMap<>();
    final Map<PsiTypeVariable, Constraint> myVarTypeConstraints = new HashMap<>();

    for (final Constraint constr : myConstraints) {
      final PsiType left = constr.getLeft();
      final PsiType right = constr.getRight();

      switch ((left instanceof PsiTypeVariable ? 0 : 1) + (right instanceof PsiTypeVariable ? 0 : 2)) {
        case 0:
          continue;

        case 1:
          {
            final Constraint c = myTypeVarConstraints.get(right);

            if (c == null) {
              final Constraint d = myVarTypeConstraints.get(right);

              if (d != null) {
                reduceInterval(constr, d);
                return;
              }

              myTypeVarConstraints.put((PsiTypeVariable)right, constr);
            }
            else {
              reduceTypeVar(constr, c);
              return;
            }
          }
          break;

        case 2:
        {
          final Constraint c = myVarTypeConstraints.get(left);

          if (c == null) {
            final Constraint d = myTypeVarConstraints.get(left);

            if (d != null) {
              reduceInterval(d, constr);
              return;
            }

            myVarTypeConstraints.put((PsiTypeVariable)left, constr);
          }
          else {
            reduceVarType(constr, c);
            return;
          }
          break;
        }

        case 3:
          reduceTypeType(constr);
          return;
      }
    }

    //T1 < a < b ... < T2
    {
      for (final Constraint constr : myConstraints) {
        final PsiType left = constr.getLeft();
        final PsiType right = constr.getRight();

        if (!(left instanceof PsiTypeVariable) && right instanceof PsiTypeVariable) {
          Set<PsiTypeVariable> bound = new PsiTypeVarCollector().getSet(left);

          if (bound.contains(right)) {
            myConstraints.remove(constr);
            mySons = new ResolverTree[]{applyRule(myBindingFactory.create(((PsiTypeVariable)right), Bottom.BOTTOM))};

            return;
          }

          final PsiManager manager = PsiManager.getInstance(myProject);
          final PsiType leftType = left instanceof PsiWildcardType ? ((PsiWildcardType)left).getBound() : left;
          final PsiType[] types = getTypeRange(PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(myProject)), leftType);

          mySons = new ResolverTree[types.length];

          if (types.length > 0) {
            myConstraints.remove(constr);
          }

          for (int i = 0; i < types.length; i++) {
            final PsiType type = types[i];
            mySons[i] = applyRule(myBindingFactory.create(((PsiTypeVariable)right), type));
          }

          return;
        }
      }
    }

    //T1 < a < b < ...
    {
      Set<PsiTypeVariable> haveLeftBound = new HashSet<>();

      Constraint target = null;
      Set<PsiTypeVariable> boundVariables = new HashSet<>();

      for (final Constraint constr : myConstraints) {
        final PsiType leftType = constr.getLeft();
        final PsiType rightType = constr.getRight();

        if (leftType instanceof PsiTypeVariable) {
          boundVariables.add((PsiTypeVariable)leftType);

          if (rightType instanceof PsiTypeVariable) {
            boundVariables.add((PsiTypeVariable)rightType);
            haveLeftBound.add(((PsiTypeVariable)rightType));
          }
          else if (!Util.bindsTypeVariables(rightType)) {
            target = constr;
          }
        }
      }

      if (target == null) {
        if (mySettings.exhaustive()) {
          for (final Constraint constr : myConstraints) {
            final PsiType left = constr.getLeft();
            final PsiType right = constr.getRight();

            PsiType[] range = null;
            PsiTypeVariable var = null;

            if (left instanceof PsiTypeVariable && !(right instanceof PsiTypeVariable)) {
              range = getTypeRange(PsiType.getJavaLangObject(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject)),
                                   right);
              var = (PsiTypeVariable)left;
            }

            if (range == null && right instanceof PsiTypeVariable && !(left instanceof PsiTypeVariable)) {
              range = new PsiType[]{right};
              var = (PsiTypeVariable)right;
            }

            if (range != null) {
              mySons = new ResolverTree[range.length];

              for (int i = 0; i < range.length; i++) {
                mySons[i] = applyRule(myBindingFactory.create(var, range[i]));
              }

              return;
            }
          }
        }

        Binding binding = myBindingFactory.create();

        for (final PsiTypeVariable var : myBindingFactory.getBoundVariables()) {
          if (!myCurrentBinding.binds(var) && !boundVariables.contains(var)) {
            binding = binding.compose(myBindingFactory.create(var, Bottom.BOTTOM));
          }
        }

        if (!binding.nonEmpty()) {
          myConstraints.clear();
        }

        mySons = new ResolverTree[]{applyRule(binding)};
      }
      else {
        final PsiType type = target.getRight();
        final PsiTypeVariable var = (PsiTypeVariable)target.getLeft();

        final Binding binding =
          (haveLeftBound.contains(var) || type instanceof PsiWildcardType) || !mySettings.cookToWildcards()
          ? myBindingFactory.create(var, type)
          : myBindingFactory.create(var, PsiWildcardType.createExtends(PsiManager.getInstance(myProject), type));

        myConstraints.remove(target);

        mySons = new ResolverTree[]{applyRule(binding)};
      }
    }
  }

  private void logSolution() {
    LOG.debug("Reduced system:");

    for (final Constraint constr : myConstraints) {
      LOG.debug(constr.toString());
    }

    LOG.debug("End of Reduced system.");
    LOG.debug("Reduced binding:");
    LOG.debug(myCurrentBinding.toString());
    LOG.debug("End of Reduced binding.");
  }

  private interface Reducer {
    LinkedList<Pair<PsiType, Binding>> unify(PsiType x, PsiType y);

    Constraint create(PsiTypeVariable var, PsiType type);

    PsiType getType(Constraint c);

    PsiTypeVariable getVar(Constraint c);
  }

  private void reduceTypeVar(final Constraint x, final Constraint y) {
    reduceSideVar(x, y, new Reducer() {
      @Override
      public LinkedList<Pair<PsiType, Binding>> unify(final PsiType x, final PsiType y) {
        return myBindingFactory.intersect(x, y);
      }

      @Override
      public Constraint create(final PsiTypeVariable var, final PsiType type) {
        return new Subtype(type, var);
      }

      @Override
      public PsiType getType(final Constraint c) {
        return c.getLeft();
      }

      @Override
      public PsiTypeVariable getVar(final Constraint c) {
        return (PsiTypeVariable)c.getRight();
      }
    });
  }

  private void reduceVarType(final Constraint x, final Constraint y) {
    reduceSideVar(x, y, new Reducer() {
      @Override
      public LinkedList<Pair<PsiType, Binding>> unify(final PsiType x, final PsiType y) {
        return myBindingFactory.union(x, y);
      }

      @Override
      public Constraint create(final PsiTypeVariable var, final PsiType type) {
        return new Subtype(var, type);
      }

      @Override
      public PsiType getType(final Constraint c) {
        return c.getRight();
      }

      @Override
      public PsiTypeVariable getVar(final Constraint c) {
        return (PsiTypeVariable)c.getLeft();
      }
    });
  }

  private void reduceSideVar(final Constraint x, final Constraint y, final Reducer reducer) {
    final PsiTypeVariable var = reducer.getVar(x);

    final PsiType xType = reducer.getType(x);
    final PsiType yType = reducer.getType(y);

    final LinkedList<Pair<PsiType, Binding>> union = reducer.unify(xType, yType);

    if (union.isEmpty()) {
      return;
    }

    myConstraints.remove(x);
    myConstraints.remove(y);

    mySons = new ResolverTree[union.size()];
    int i = 0;

    Constraint prev = null;

    for (final Pair<PsiType, Binding> pair : union) {
      if (prev != null) {
        myConstraints.remove(prev);
      }

      prev = reducer.create(var, pair.getFirst());
      myConstraints.add(prev);

      mySons[i++] = applyRule(pair.getSecond());
    }
  }

  public void resolve() {
    reduce();

    if (mySons.length > 0) {
      for (int i = 0; i < mySons.length; i++) {

        if (mySons[i] != null) {
          mySons[i].resolve();
          if (!mySettings.exhaustive() && mySettings.cookToWildcards() && mySons[i].mySolutionFound) break;
          mySons[i] = null;
        }
      }
    }
    else {
      if (myConstraints.isEmpty()) {
        logSolution();

        mySolutions.putSolution(myCurrentBinding);
        mySolutionFound = true;
      }
    }
  }

  public Binding getBestSolution() {
    return mySolutions.getBestSolution();
  }
}
