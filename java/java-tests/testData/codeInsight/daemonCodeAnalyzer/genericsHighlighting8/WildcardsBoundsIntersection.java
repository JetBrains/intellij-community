class NodeProperty<A, B> {}

class NodeType {}
class NumberExpression extends NodeType {}
class Node<NodeTypeT extends NodeType> {
    public <ValueT> ValueT get(NodeProperty<? super NodeTypeT, ValueT> prop) {
        return null;
    }
}

class Main {
    public static void main(NodeProperty<NumberExpression, Integer> nval,  Node<? extends NodeType> expr) {
        int val = expr.get<error descr="'get(NodeProperty<? super capture<? extends NodeType>,ValueT>)' in 'Node' cannot be applied to '(NodeProperty<NumberExpression,java.lang.Integer>)'">(nval)</error>;
    }
}
