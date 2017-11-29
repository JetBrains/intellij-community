import java.util.ArrayList;
import java.util.Comparator;

public final class Main {
  public static void main(final String[] args) {
    new ArrayList<Node>().sort(Comparator.comparingInt((N<caret>ode node) -> node.value).reversed());
  }

  static class Node {
    private final int value;

    Node(final int value) {
      this.value = value;
    }
  }
}