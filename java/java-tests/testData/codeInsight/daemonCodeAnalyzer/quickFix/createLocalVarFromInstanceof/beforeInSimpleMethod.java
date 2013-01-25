// "Insert '(NodeInfo)parent' declaration" "true"
public abstract class A {
    public void getNodeElements(Object parent) {
        if (!(parent instanceof <caret>NodeInfo)) return;
    }

    private static class NodeInfo {
    }
}
