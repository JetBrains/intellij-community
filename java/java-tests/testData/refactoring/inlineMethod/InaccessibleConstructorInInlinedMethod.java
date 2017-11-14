class InlineWithPrivateConstructorAccess {
    public static class SomeClass {

        private SomeClass() { }

        public static SomeClass createInstance() {
            return new SomeClass();
        }

    }

}

class InlineWithPrivateConstructorAccessMain {

    public static void main(String... args) {
        InlineWithPrivateConstructorAccess.SomeClass obj = InlineWithPrivateConstructorAccess.SomeClass.create<caret>Instance();

    }

}