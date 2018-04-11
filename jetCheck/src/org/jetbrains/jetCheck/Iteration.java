package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

class Iteration<T> {

  private static final Predicate<Object> DATA_IS_DIFFERENT = new Predicate<Object>() {
    @Override
    public boolean test(Object o) {
      return false;
    }

    @Override
    public String toString() {
      return ": cannot generate enough sufficiently different values";
    }
  };

  final CheckSession<T> session;
  long iterationSeed;
  final int sizeHint;
  final int iterationNumber;
  private Random random;

  Iteration(CheckSession<T> session, long iterationSeed, int iterationNumber) {
    this.session = session;
    this.sizeHint = session.sizeHintFun.applyAsInt(iterationNumber);
    this.iterationNumber = iterationNumber;
    if (sizeHint < 0) {
      throw new IllegalArgumentException("Size hint should be non-negative, found " + sizeHint);
    }
    initSeed(iterationSeed);
  }

  private void initSeed(long seed) {
    iterationSeed = seed;
    random = new Random(seed);
  }

  @Nullable
  private CounterExampleImpl<T> findCounterExample() {
    for (int i = 0; i < 100; i++) {
      if (i > 0) {
        initSeed(random.nextLong());
      }
      StructureNode node = new StructureNode(new NodeId(session.generator));
      T value;
      try {
        value = session.generator.getGeneratorFunction().apply(new GenerativeDataStructure(random, node, sizeHint));
      }
      catch (CannotSatisfyCondition e) {
        continue;
      }
      catch (Throwable e) {
        throw new GeneratorException(this, e);
      }
      if (!session.generatedHashes.add(node.hashCode())) continue;

      return CounterExampleImpl.checkProperty(this, value, node);
    }
    throw new GeneratorException(this, new CannotSatisfyCondition(DATA_IS_DIFFERENT));
  }

  String printToReproduce(@Nullable Throwable failureReason) {
    String rechecking = failureReason != null && StatusNotifier.printStackTrace(failureReason).contains("ImperativeCommand.checkScenario") ?
      "ImperativeCommand.checkScenario(" + iterationSeed + "L, " + sizeHint + ", ...))\n" :
      "PropertyChecker.forAll(...).rechecking(" + iterationSeed + "L, " + sizeHint + ").shouldHold(...)\n";
    return "To reproduce the last iteration, run " + rechecking + "Global seed: " + session.globalSeed + "L";
  }

  String printSeeds() {
    return "iteration seed=" + iterationSeed + "L, " +
           "size hint=" + sizeHint + ", " +
           "global seed=" + session.globalSeed + "L";
  }

  @Nullable
  Iteration<T> performIteration() {
    session.notifier.iterationStarted(iterationNumber);

    CounterExampleImpl<T> example = findCounterExample();
    if (example != null) {
      session.notifier.counterExampleFound(this);
      throw new PropertyFalsified(new PropertyFailureImpl<>(example, this));
    }

    if (iterationNumber >= session.iterationCount) {
      return null;
    }
    
    return new Iteration<>(session, random.nextLong(), iterationNumber + 1);
  }

  T generateValue(ReplayDataStructure data) {
    return session.generator.getGeneratorFunction().apply(data);
  }
}

class CheckSession<T> {
  final Generator<T> generator;
  final Predicate<T> property;
  final long globalSeed;
  final Set<Integer> generatedHashes = new HashSet<>();
  final StatusNotifier notifier;
  final int iterationCount;
  final IntUnaryOperator sizeHintFun;

  CheckSession(Generator<T> generator, Predicate<T> property, long globalSeed, int iterationCount, IntUnaryOperator sizeHintFun, boolean silent) {
    this.generator = generator;
    this.property = property;
    this.globalSeed = globalSeed;
    this.iterationCount = iterationCount;
    this.sizeHintFun = sizeHintFun;
    notifier = silent ? StatusNotifier.SILENT : new StatusNotifier(iterationCount);
  }

  Iteration<T> firstIteration() {
    return new Iteration<>(this, globalSeed, 1);
  }
}
