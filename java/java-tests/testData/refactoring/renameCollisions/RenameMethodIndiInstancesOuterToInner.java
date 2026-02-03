public class RenameCollisions {
    public void <caret>method() {
    }

    public static class StaticInnerClass {
        public void siMethod() {
        }
        public void instanceContext() {
            siMethod();
        }
    }

    public void instanceContext() {
        method();
    }
}
