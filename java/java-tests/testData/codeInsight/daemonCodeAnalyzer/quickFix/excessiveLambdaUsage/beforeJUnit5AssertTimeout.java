// "Fix all 'Excessive lambda usage' problems in file" "false"
package org.junit.jupiter.api;

interface Supplier<T> {
    T supply();
}

class Assertions {
    static void assertTimeout(Object duration, String message) {};
    static void assertTimeout(Object duration, Supplier<String> message) {};
}

class Test {
    public void test() {
        Assertions.assertTimeout(null, () -<caret>> "a result");
    }
}