class Base { }
class Extended extends Base {}

class Test<T extends Base> {
    <T extends Base, U extends T> void test(T x, Class<U> test) {}
    {
        <error descr="Inferred type 'Base' for type parameter 'U' is not within its bound; should extend 'Extended'">test(new Extended(), Base.class)</error>;
    }
}