public class Foo<T> {
    void m() {
        Foo<Foo> foo = new Foo<Foo>();<caret>
    }
}