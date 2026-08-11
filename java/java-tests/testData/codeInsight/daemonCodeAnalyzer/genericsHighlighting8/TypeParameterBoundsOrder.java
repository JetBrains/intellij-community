// Since Java 8, javac compares the bounds of a type parameter as a set, so the order in which they are declared
// does not affect signature equality. See genericsHighlighting/TypeParameterBoundsOrder.java for the older behavior.
import java.io.Serializable;

class Base {
    public <A extends Serializable & CharSequence> void foo(A a) {
    }
}

class Jaba extends Base {
    @Override
    public <A extends CharSequence & Serializable> void foo(A a) {
    }
}

class Dup {
    <error descr="'foo(T)' is already defined in 'Dup'"><T extends Serializable & CharSequence> void foo(T t)</error> {
    }

    <error descr="'foo(T)' is already defined in 'Dup'"><T extends CharSequence & Serializable> void foo(T t)</error> {
    }
}
