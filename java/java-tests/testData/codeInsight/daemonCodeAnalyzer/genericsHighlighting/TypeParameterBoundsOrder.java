// The order of the bounds of a type parameter never affected overriding, but javac 7 and older did not reject two methods
// which differ only in that order. See genericsHighlighting8/TypeParameterBoundsOrder.java for the Java 8+ behavior.
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
    <T extends Serializable & CharSequence> void foo(T t) {
    }

    <T extends CharSequence & Serializable> void foo(T t) {
    }
}
