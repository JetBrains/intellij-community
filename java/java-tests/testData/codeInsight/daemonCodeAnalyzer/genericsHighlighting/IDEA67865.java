import java.util.*;

abstract class A {
    static <T> void foo(List<T> x) { }
    static <T extends List<?>> void foo(Collection<T> x) { }
    public static void main(String[] args){
        List<List<String>> x = null;
        foo<error descr="Ambiguous method call: both 'A.foo(List<List<String>>)' and 'A.foo(Collection<List<String>>)' match">(x)</error>;
        foo<error descr="Ambiguous method call: both 'A.foo(List<Object>)' and 'A.foo(Collection<List<?>>)' match">(null)</error>;
    }
}
