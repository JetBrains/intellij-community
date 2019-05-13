import java.util.List;

<error descr="'method(List<String>)' in 'Implementation' clashes with 'method(List<String>)' in 'IfcWithGenericMethod'; both methods have same erasure, yet neither overrides the other">class Implementation implements IfcWithGenericMethod</error> {
    public void method(final List<String> strings) {}
}

interface IfcWithGenericMethod<T> {
    void method(List<String> strings);
}