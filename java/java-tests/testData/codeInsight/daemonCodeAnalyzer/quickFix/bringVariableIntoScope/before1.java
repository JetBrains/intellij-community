// "Bring 'int i' into scope" "true"
class a {
    void foo () {
        try {
            int i = 0;
        } catch (Exception e) {
            int j = <caret>i;
        }
    }
}