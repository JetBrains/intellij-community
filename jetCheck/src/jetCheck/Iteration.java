package jetCheck;

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
  final long iterationSeed;
  final int sizeHint;
  final int iterationNumber;

  Iteration(CheckSession<T> session, long iterationSeed, int iterationNumber) {
    this.session = session;
    this.iterationSeed = iterationSeed;
    this.sizeHint = session.sizeHintFun.applyAsInt(iterationNumber);
    this.iterationNumber = iterationNumber;
    if (sizeHint < 0) {
      throw new IllegalArgumentException("Size hint should be non-negative, found " + sizeHint);
    }
  }

  @Nullable
  private CounterExampleImpl<T> findCounterExample(Random random) {
    for (int i = 0; i < 100; i++) {
      StructureNode node = new StructureNode();
      T value;
      try {
        value = session.generator.getGeneratorFunction().apply(new GenerativeDataStructure(random, node, sizeHint));
      }
      catch (Throwable e) {
        throw new GeneratorException(this, e);
      }
      if (!session.generatedHashes.add(node.hashCode())) continue;

      return CounterExampleImpl.checkProperty(session.property, value, node);
    }
    throw new CannotSatisfyCondition(DATA_IS_DIFFERENT);
  }

  String printToReproduce() {
    return "To reproduce the last iteration, run PropertyChecker.forAll(...).rechecking(" + iterationSeed + "L, " + sizeHint + ").shouldHold(...)\n" +
           "Global seed: " + session.globalSeed + "L";
  }

  String printSeeds() {
    return "iteration seed=" + iterationSeed + "L, " +
           "size hint=" + sizeHint + ", " +
           "global seed=" + session.globalSeed + "L";
  }

  @Nullable
  Iteration<T> performIteration() {
    session.notifier.iterationStarted(iterationNumber);

    Random random = new Random(iterationSeed);
    CounterExampleImpl<T> example = findCounterExample(random);
    if (example != null) {
      session.notifier.counterExampleFound(this);
      PropertyFailureImpl<T> failure = new PropertyFailureImpl<>(example, this);
      throw new PropertyFalsified(failure, () -> new ReplayDataStructure(failure.getMinimalCounterexample().data, sizeHint));
    }

    if (iterationNumber >= session.iterationCount) {
      return null;
    }
    
    return new Iteration<>(session, random.nextLong(), iterationNumber + 1);
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

  CheckSession(Generator<T> generator, Predicate<T> property, long globalSeed, int iterationCount, IntUnaryOperator sizeHintFun) {
    this.generator = generator;
    this.property = property;
    this.globalSeed = globalSeed;
    this.iterationCount = iterationCount;
    this.sizeHintFun = sizeHintFun;
    notifier = new StatusNotifier(iterationCount);
  }

  Iteration<T> firstIteration() {
    return new Iteration<>(this, globalSeed, 1);
  }
}
