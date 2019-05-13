// "Use 'assertEquals' method without lambda" "true"
package org.junit.jupiter.api;

interface Supplier<T> {
    T supply();
}

class Assertions {
    static void assertEquals(int expected, int actual, String message) {};
    static void assertEquals(int expected, int actual, Supplier<String> message) {};
}

class Test {
    public void test() {
        Assertions.assertEquals(4, 2+2, () <caret>-> "Math works!");
    }
}