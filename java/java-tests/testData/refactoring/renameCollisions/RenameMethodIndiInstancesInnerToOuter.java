public class RenameCollisions {
    public void method() {
    }

    public static class StaticInnerClass {
        public void <caret>siMethod() {
        }
        public void instanceContext() {
            siMethod();
        }
    }

    public void instanceContext() {
        method();
    }
}
