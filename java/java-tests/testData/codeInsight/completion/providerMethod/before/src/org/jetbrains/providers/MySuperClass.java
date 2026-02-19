package org.jetbrains.providers;

public class MySuperClass {
    public static class MySubProviderImpl extends MyProviderImpl {
        public MySubProviderImpl() {
        }

        pr<caret>public static void method() {
        }
    }
}
