import java.util.List;
class MyTest {
    void f(List<Integer> integerList, List<String> stringList) {}

    void m(List<Integer> integerList, List<String> stringList) {
        f<error descr="'f(java.util.List<java.lang.Integer>, java.util.List<java.lang.String>)' in 'MyTest' cannot be applied to '(java.util.List<java.lang.String>, java.util.List<java.lang.Integer>)'">(stringList, integerList)</error>;
    }
}