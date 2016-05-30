import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

class Main {
  public static void main(String[] args) {
    Queue<Node> q = new PriorityQueue<>(Comparator.comparing(node -> node.n));
  }
}

class Node {
  final int n;

  Node(int n) {
    this.n = n;
  }
}
