class Base { }
class Extended extends Base {}

class Test<T extends Base> {
    <T extends Base, U extends T> void test(T x, Class<U> test) {}
    {
        test(new Extended(), Base.class);
    }
}
