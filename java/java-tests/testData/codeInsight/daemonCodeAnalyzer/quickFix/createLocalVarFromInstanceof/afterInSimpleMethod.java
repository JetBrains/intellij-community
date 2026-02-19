// "Insert '(NodeInfo)parent' declaration" "true-preview"
public abstract class A {
    public void getNodeElements(Object parent) {
        if (!(parent instanceof NodeInfo)) return;
        NodeInfo nodeInfo = (NodeInfo) parent;
        <caret>
    }

    private static class NodeInfo {
    }
}
