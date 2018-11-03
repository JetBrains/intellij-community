class Test {

    Node findRoot(Node n) {
        while(n.getParent() != null) {
            n = <selection>n.getParent()</selection>;
        }
        return n;
    }
}
interface Node {
    Node getParent();
}
