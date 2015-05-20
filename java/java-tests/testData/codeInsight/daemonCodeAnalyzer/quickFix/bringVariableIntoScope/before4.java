// "Bring 'int i' into scope" "true"
class a {
    public void foo() {
        if (true) {
            int i = 0;
        } else {
            int j = <caret>i;
        }
    }
}