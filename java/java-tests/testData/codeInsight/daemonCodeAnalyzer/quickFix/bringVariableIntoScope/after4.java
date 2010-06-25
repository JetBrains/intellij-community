// "Bring 'int i' into Scope" "true"
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