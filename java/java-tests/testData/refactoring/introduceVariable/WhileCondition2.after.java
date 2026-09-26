class Test {

    boolean native isCancelled();

    Node findRoot(Node n) {
        while(!isCancelled()) {
            Node temp = n.getParent();
            if (!(temp != null)) break;
            n = temp;
        }
        return n;
    }
}
interface Node {
    Node getParent();
}
