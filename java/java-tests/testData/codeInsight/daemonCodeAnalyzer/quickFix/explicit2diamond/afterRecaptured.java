// "Replace with <>" "true"
interface I<T> {
    I<T> then(T t);
}
class J<T extends String> {}
class MyTest {

    <K> I<K> when(K p) { return null;}



    void m(J<?> l){

        when(l).then((J) new J<>());
    }

}