class SOE {
}
abstract class VersionEntity<V extends Version<V, R>, R extends Ref<V, R>>
    implements Version<V, R>{}

interface Version<V extends Version<V, R>, R extends Ref<V, R>>{}

abstract class RefEntity<V extends Version<V, R>, R extends Ref<V, R>>
    implements Ref<V, R> {}

interface Ref<V extends Version<V, R>, R extends Ref<V, R>>{}


abstract class Node<G extends Node<G, GR>,
               GR extends NodeRef<G, GR>> extends VersionEntity<G, GR> {}

abstract class NodeRef<G extends Node<G, GR>, GR extends NodeRef<G, GR>> extends RefEntity<G, GR> {}


class D {
    void f() {
        Version v = new Node<<error descr="Wildcard type '?' cannot be instantiated directly">?</error>, <error descr="Wildcard type '?' cannot be instantiated directly">?</error>>(){}<EOLError descr="';' expected"></EOLError>
        v.hashCode();
    }
}
