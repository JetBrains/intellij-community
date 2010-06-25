// "Bring 'int i' into Scope" "true"
class a {
    void foo () {
        int i = 0;
        try {
            i = 0;
        } catch (Exception e) {
            int j = i;
        }
    }
}