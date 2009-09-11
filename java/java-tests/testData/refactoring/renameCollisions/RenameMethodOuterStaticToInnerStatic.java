public class RenameCollisions {
    public static void staticMethod<caret>() {
    }

    public static class StaticInnerClass {
        public static void siStaticMethod() {
        }
        public void instanceContext() {
            staticMethod();
            siStaticMethod();
        }
        public static void staticContext() {
            staticMethod();
            siStaticMethod();
        }
    }
}
