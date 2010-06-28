// "Create Constructor" "true"
class Test {

    public void t() {
        new Inner("a"){};
    }

    class Inner {
        public Inner(String s) {
            <selection>//To change body of created methods use File | Settings | File Templates.</selection><caret>
        }
    }
}