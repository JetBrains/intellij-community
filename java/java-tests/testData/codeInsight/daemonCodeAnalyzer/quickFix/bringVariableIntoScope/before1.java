// "Bring 'int i' into scope" "true-preview"
class a {
    void foo () {
        try {
            int i = 0;
        } catch (Exception e) {
            int j = <caret>i;
        }
    }
}