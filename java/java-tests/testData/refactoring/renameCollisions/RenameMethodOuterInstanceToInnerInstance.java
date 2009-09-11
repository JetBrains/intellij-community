public class RenameCollisions {
    public void <caret>method() {
    }
    class InnerClass {
        public void innerMethod() {
        }
        public void instanceContext() {
            method();
            innerMethod();
        }
    }
}
