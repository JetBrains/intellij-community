// "Bring 'int i' into scope" "true-preview"
class a {
    public void foo() {
        for (int i = 0; i < 10; i++) {  }
        <caret>i = 0;
    }
}