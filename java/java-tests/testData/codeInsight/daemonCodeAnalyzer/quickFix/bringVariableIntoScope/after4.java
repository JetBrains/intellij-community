// "Bring 'int i' into scope" "true-preview"
class a {
    public void foo() {
        int i;
        if (true) {
            i = 0;
        } else {
            int j = i;
        }
    }
}