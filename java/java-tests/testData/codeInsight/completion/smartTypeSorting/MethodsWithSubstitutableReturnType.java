public class Foo<T> {

   T foo() {}

   <V> V bar() {}

    {
        Foo<String> o;
        String a = o.<caret>
    }
}