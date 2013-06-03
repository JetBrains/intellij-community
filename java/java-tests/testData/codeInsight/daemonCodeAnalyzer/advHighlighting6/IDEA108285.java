class Test<T> {
        interface NestedInterface<U extends <error descr="'Test.this' cannot be referenced from a static context">T</error>> {
                <V extends <error descr="'Test.this' cannot be referenced from a static context">T</error>> void foo();
        }
}