package org.jetbrains.jetCheck;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
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
    this.sizeHint = session.parameters.sizeHintFun.applyAsInt(iterationNumber);
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
        IntSource source = session.parameters.serializedData != null ? session.parameters.serializedData : d -> d.generateInt(random);
        value = session.generator.getGeneratorFunction().apply(new GenerativeDataStructure(source, node, sizeHint));
      }
      catch (CannotSatisfyCondition e) {
        continue;
      }
      catch (DataSerializer.EOFException e) {
        session.notifier.eofException();
        return null;
      }
      catch (Throwable e) {
        //noinspection InstanceofCatchParameter
        if (e instanceof CannotRestoreValue && session.parameters.serializedData != null) {
          throw e;
        }
        throw new GeneratorException(this, e);
      }
      if (!session.generatedNodes.add(node)) continue;

      return CounterExampleImpl.checkProperty(this, value, node);
    }
    throw new GeneratorException(this, new CannotSatisfyCondition(DATA_IS_DIFFERENT));
  }

  String printToReproduce(@Nullable Throwable failureReason, CounterExampleImpl<?> minimalCounterExample) {
    String data = minimalCounterExample.getSerializedData();
    boolean scenarios =
      failureReason != null && StatusNotifier.printStackTrace(failureReason).contains("PropertyChecker$Parameters.checkScenarios");
    String rechecking = "PropertyChecker.customized().rechecking(\"" + data + "\")\n    ." + 
                        (scenarios ? "checkScenarios" : "forAll") + "(...)\n";
    return "To reproduce the minimal failing case, run\n  " + rechecking +
           "To re-run the test with all intermediate minimization steps, use `recheckingIteration("  + iterationSeed + "L, " + sizeHint + ")` instead for last iteration, or `withSeed(" + session.parameters.globalSeed + "L)` for all iterations";
  }

  String printSeeds() {
    return "iteration seed=" + iterationSeed + "L, " +
           "size hint=" + sizeHint + ", " +
           "global seed=" + session.parameters.globalSeed + "L";
  }

  @Nullable
  Iteration<T> performIteration() {
    session.notifier.iterationStarted(iterationNumber);

    CounterExampleImpl<T> example = findCounterExample();
    if (example != null) {
      session.notifier.counterExampleFound(this);
      throw new PropertyFalsified(new PropertyFailureImpl<>(example, this));
    }

    if (iterationNumber >= session.parameters.iterationCount) {
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
  final PropertyChecker.Parameters parameters;
  final Set<StructureNode> generatedNodes = new HashSet<>();
  final StatusNotifier notifier;

  CheckSession(Generator<T> generator, Predicate<T> property, PropertyChecker.Parameters parameters) {
    this.generator = generator;
    this.property = property;
    this.parameters = parameters;
    notifier = parameters.silent ? StatusNotifier.SILENT : new StatusNotifier(parameters.iterationCount);
  }

  Iteration<T> firstIteration() {
    return new Iteration<>(this, parameters.globalSeed, 1);
  }
}
