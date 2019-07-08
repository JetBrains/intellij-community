class A<S> {
    <T> T foo(T x, S y){
        return x;
    }
}

class B<S> extends A<S> {
    Object foo(Object x, Object y){
        return x;
    }
}

<error descr="'foo(T, S)' in 'A' clashes with 'foo(Object, Object)' in 'B'; both methods have same erasure, yet neither overrides the other">class C extends B<String></error> {
    @Override
    <T> T foo(T x, String y) {
        <error descr="Incompatible types. Found: 'java.lang.Object', required: 'T'">return super.foo(x, y);</error>
    }
}