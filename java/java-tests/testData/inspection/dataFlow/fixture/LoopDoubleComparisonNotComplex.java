import java.util.List;

class LoopDoubleComparisonNotComplex {
  private static Node prob(List<Node> nodes) {
    Node maxNode = null;
    double maxProbability = 0.0;

    for (Node node : nodes) {
      double probability = probability(node);
      if (probability > maxProbability) {
        maxProbability = probability;
        maxNode = node;
      } else if (probability == maxProbability) {
      }
    }

    return <warning descr="Expression 'maxNode' might evaluate to null but is returned by the method which is not declared as @Nullable">maxNode</warning>;
  }

  interface Node {}

  static double probability(Node node) {
    return 0.0;
  }

}
