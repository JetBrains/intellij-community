package org.jetbrains.providers;

public class MySuperClass {
    public static class MySubProviderImpl extends MyProviderImpl {
        public MySubProviderImpl() {
        }

        public static MySubProviderImpl provider() {
            return new MySubProviderImpl(<caret>);
        }

        public static void method() {
        }
    }
}
