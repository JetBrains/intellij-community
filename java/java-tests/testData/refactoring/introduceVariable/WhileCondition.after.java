class Test {

    Node findRoot(Node n) {
        while(true) {
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
