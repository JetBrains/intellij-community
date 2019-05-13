// "Split into declaration and assignment" "true"

class Foo {
    void some() {
        Foo<T> <caret>f = new Foo<T>(){
            <T,V> void foo(T t, V v) {}
        }
   }
}