// "Create constructor" "true-preview"
class Test {

    public void t() {
        new Inner("a"){};
    }

    class Inner {
        public Inner(String a) {
            <caret><selection></selection>
        }
    }
}