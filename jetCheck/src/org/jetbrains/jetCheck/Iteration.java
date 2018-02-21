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
        IntSource source = session.serializedData != null ? session.serializedData : d -> d.generateInt(random);
        value = session.generator.getGeneratorFunction().apply(new GenerativeDataStructure(source, node, sizeHint));
      }
      catch (CannotSatisfyCondition e) {
        continue;
      }
      catch (Throwable e) {
        throw new GeneratorException(this, e);
      }
      if (!session.generatedNodes.add(node)) continue;

      return CounterExampleImpl.checkProperty(this, value, node);
    }
    throw new GeneratorException(this, new CannotSatisfyCondition(DATA_IS_DIFFERENT));
  }

  String printToReproduce(@Nullable Throwable failureReason, CounterExampleImpl<?> minimalCounterExample) {
    String data = minimalCounterExample.getSerializedData();
    String rechecking = failureReason != null && StatusNotifier.printStackTrace(failureReason).contains("ImperativeCommand.checkScenario") ?
      "ImperativeCommand.checkScenario(\n    \"" + data + "\", \n    ...))\n" :
      "PropertyChecker.forAll(...)\n    .rechecking(\"" + data + "\")\n    .shouldHold(...)\n";
    return "To reproduce the minimal failing case, run\n  " + rechecking +
           "To re-run the test with all intermediate minimization steps, use `recheckingIteration("  + iterationSeed + "L, " + sizeHint + ")` instead for last iteration, or `withSeed(" + session.globalSeed + "L)` for all iterations";
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
  final Set<StructureNode> generatedNodes = new HashSet<>();
  final StatusNotifier notifier;
  final int iterationCount;
  final IntUnaryOperator sizeHintFun;
  @Nullable final IntSource serializedData;

  CheckSession(Generator<T> generator, Predicate<T> property, long globalSeed, int iterationCount, IntUnaryOperator sizeHintFun, boolean silent,
               @Nullable IntSource serializedData) {
    this.generator = generator;
    this.property = property;
    this.globalSeed = globalSeed;
    this.iterationCount = iterationCount;
    this.sizeHintFun = sizeHintFun;
    this.serializedData = serializedData;
    notifier = silent ? StatusNotifier.SILENT : new StatusNotifier(iterationCount);
  }

  Iteration<T> firstIteration() {
    return new Iteration<>(this, globalSeed, 1);
  }
}
