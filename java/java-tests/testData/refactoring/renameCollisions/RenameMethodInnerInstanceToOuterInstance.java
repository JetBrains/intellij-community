public class RenameCollisions {
    public void method() {
    }
    class InnerClass {
        public void <caret>innerMethod() {
        }
        public void instanceContext() {
            method();
            innerMethod();
        }
    }
}
