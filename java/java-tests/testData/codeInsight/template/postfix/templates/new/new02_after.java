public class Foo<T> {
    void m() {
        new Foo<Integer>();<caret>
    }
}