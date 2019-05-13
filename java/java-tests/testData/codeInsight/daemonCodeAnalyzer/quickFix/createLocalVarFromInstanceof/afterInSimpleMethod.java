// "Insert '(NodeInfo)parent' declaration" "true"
public abstract class A {
    public void getNodeElements(Object parent) {
        if (!(parent instanceof NodeInfo)) return;
        NodeInfo parent1 = (NodeInfo) parent;
        <caret>
    }

    private static class NodeInfo {
    }
}
