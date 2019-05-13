public class Main {
    interface I<T> {
        void m(T t);
    }

    static void foo() {}

    {
        String s = "";
        I<Object> arr1 = (t) -> <error descr="Bad return type in lambda expression: String cannot be converted to void">s</error>;
        I<Object> arr2 = (t) -> s.toString();
      
        I<Integer> i1 = i -> <error descr="Bad return type in lambda expression: int cannot be converted to void">i * 2</error>;
        I<Integer> i2 = i -> <error descr="Bad return type in lambda expression: int cannot be converted to void">2 * i</error>;
        I<Integer> i3 = i -> <error descr="Lambda body must be a statement expression">true ? foo() : foo()</error>;
    }

}
