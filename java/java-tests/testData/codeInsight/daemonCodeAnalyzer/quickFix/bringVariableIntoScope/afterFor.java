// "Bring 'int i' into scope" "true"
class a {
    public void foo() {
        int i;
        for (i = 0; i < 10; i++) {
        }
        i = 0;
    }
}