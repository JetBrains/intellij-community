import java.util.*;
class Node<K> {
    List<Node<K>> getNodes() {
        return null;
    }

    private static <T> int strongConnect(Node<T> currentNode) {
        for (Node<T> dependantNode : currentNode.getNodes()) {
            strongConnect(dependantNode);
        }
        return 0;
    }
}
