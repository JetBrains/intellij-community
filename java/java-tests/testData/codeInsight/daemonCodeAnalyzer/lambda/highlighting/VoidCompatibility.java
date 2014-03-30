public class Main {
    interface I<T> {
        void m(T t);
    }

    static void foo() {}

    {
        String s = "";
        I<Object> arr1 = <error descr="Incompatible return type String in lambda expression">(t) -> s</error>;
        I<Object> arr2 = (t) -> s.toString();
      
        I<Integer> i1 = <error descr="Incompatible return type int in lambda expression">i -> i * 2</error>;
        I<Integer> i2 = <error descr="Incompatible return type int in lambda expression">i -> 2 * i</error>;
        I<Integer> i3 = <error descr="Incompatible return type void in lambda expression">i -> true ? foo() : foo()</error>;
    }

}
