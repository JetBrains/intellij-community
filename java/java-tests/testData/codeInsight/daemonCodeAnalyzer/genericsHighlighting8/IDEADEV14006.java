/** @noinspection UnusedDeclaration*/
interface TestIF2<T> extends TestIF3<T> {}

/** @noinspection UnusedDeclaration*/
interface TestIF<T extends TestIF2<? extends Test2>> {
    void run(T o1);
}

/** @noinspection UnusedDeclaration*/
interface TestIF3<T> {}

class Test2 {}

class Test {
    public void test(TestIF<?> testIF) {
        testIF.run<error descr="'run(capture<?>)' in 'TestIF' cannot be applied to '()'">()</error>;
    }
}
