// "Use 'or' method without lambda" "true-preview"
package com.google.common.base;

interface Supplier<T> {
    T supply();
}

abstract class Optional<T> {
    abstract T or(T value);
    abstract T or(Supplier<? extends T> supplier);
}

class Test {
    public void test(Optional<String> opt) {
        System.out.println(opt.or(() <caret>-> ""));
    }
}