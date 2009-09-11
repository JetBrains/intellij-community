public class RenameCollisions {
    public static void staticMethod() {
    }
    public static class StaticInnerClass {
        public static void siStaticMethod<caret>() {
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
