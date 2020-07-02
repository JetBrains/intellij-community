class Outer {
    static class Nested {
        {
            newMethod();
        }

        private static void newMethod() {
            int i = 0;
        }
    }
}