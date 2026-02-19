package com.siyeh.igtest.abstraction;

class TestFeatureEnvySuper {
    void foo1() {
    }

    void foo2() {
    }

    void foo3() {
    }
}

class TestFeatureEnvySuper2 {
    void test() {
        TestFeatureEnvySuper test = new TestFeatureEnvySuper();
        test.foo1();
        test.foo2();
        test.foo3();
    }
}

public class FeatureEnvy extends TestFeatureEnvySuper {
    class Inner {
        void test() {
            foo1();
            foo2();
            foo3();
        }
    }
}