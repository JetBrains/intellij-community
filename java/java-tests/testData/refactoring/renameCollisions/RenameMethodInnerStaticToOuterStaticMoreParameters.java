public class RenameCollisions {
    public static void staticMethod() {
    }
    public static class StaticInnerClass {
        public static void siStaticMethod<caret>(String s) {
        }
        public void instanceContext() {
            staticMethod();
            siStaticMethod("a");
        }
        public static void staticContext() {
            staticMethod();
            siStaticMethod("b");
        }
    }
}
