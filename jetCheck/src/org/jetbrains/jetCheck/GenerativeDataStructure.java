package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.function.Predicate;


/**
 * @author peter
 */
class GenerativeDataStructure extends AbstractDataStructure {
  private final Random random;

  GenerativeDataStructure(Random random, StructureNode node, int sizeHint) {
    super(node, sizeHint);
    this.random = random;
  }

  @Override
  public int drawInt(@NotNull IntDistribution distribution) {
    int i = distribution.generateInt(random);
    node.addChild(new IntData(node.id.childId(null), i, distribution));
    return i;
  }

  @NotNull
  @Override
  GenerativeDataStructure subStructure(@NotNull Generator<?> generator) {
    return new GenerativeDataStructure(random, node.subStructure(generator), childSizeHint());
  }

  @Override
  public <T> T generateNonShrinkable(@NotNull Generator<T> generator) {
    GenerativeDataStructure data = subStructure(generator);
    data.node.shrinkProhibited = true;
    return generator.getGeneratorFunction().apply(data);
  }

  @Override
  public <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition) {
    for (int i = 0; i < 100; i++) {
      GenerativeDataStructure structure = new GenerativeDataStructure(random, node.subStructure(generator), childSizeHint());
      T value = generator.getGeneratorFunction().apply(structure);
      if (condition.test(value)) return value;
      
      node.removeLastChild(structure.node);
    }
    throw new CannotSatisfyCondition(condition);
  }
}
