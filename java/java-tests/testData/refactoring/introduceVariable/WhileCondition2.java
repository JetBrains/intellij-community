class Test {

    boolean native isCancelled();

    Node findRoot(Node n) {
        while(!isCancelled() && n.getParent() != null) {
            n = <selection>n.getParent()</selection>;
        }
        return n;
    }
}
interface Node {
    Node getParent();
}
