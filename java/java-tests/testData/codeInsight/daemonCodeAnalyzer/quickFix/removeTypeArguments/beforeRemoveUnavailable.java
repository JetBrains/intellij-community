// "Remove type arguments" "false"
abstract class SomeClass<K, T> implements Some<K, T> {
    public abstract void doSomething(K key, Node<caret><K, T> root);
}
class Node<G> {}
interface Some<II, OO>{}

