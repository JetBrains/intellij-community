interface A {
    <T extends Exception> void foo() throws T;
    <T extends Exception> void bar() throws Exception;
    void baz() throws Exception;
}

interface B<T extends Throwable> extends A {
    void foo() throws <error descr="'foo()' in 'B' clashes with 'foo()' in 'A'; overridden method does not throw 'T'">T</error>;
    void bar() throws <error descr="'bar()' in 'B' clashes with 'bar()' in 'A'; overridden method does not throw 'java.lang.Throwable'">Throwable</error>;
    void baz() throws <error descr="'baz()' in 'B' clashes with 'baz()' in 'A'; overridden method does not throw 'java.lang.Throwable'">Throwable</error>;
}
