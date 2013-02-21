public class Main {
    interface I<T> {
        void m(T t);
    }

    {
        String s = "";
        I<Object> arr1 = <error descr="Incompatible return type String in lambda expression">(t) -> s</error>;
        I<Object> arr2 = (t) -> s.toString();
    }

}
