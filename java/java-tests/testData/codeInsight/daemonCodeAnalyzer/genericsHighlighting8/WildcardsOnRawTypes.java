import java.util.List;

class Main<T> {
    Object get(List<DiagramNode<T>> nodes, A a) {
        return null;
    }
}
class DiagramNode<T> {}

class A {
    static void f(Main m, List<DiagramNode<?>> nodes){
        final Object data = m.get(nodes, new A());
        final <error descr="Incompatible types. Found: 'java.util.List<DiagramNode<?>>', required: 'java.util.List<DiagramNode>'">List<DiagramNode> n = nodes;</error>
    }
}
