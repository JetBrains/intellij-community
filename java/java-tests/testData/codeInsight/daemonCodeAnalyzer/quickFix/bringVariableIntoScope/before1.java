// "Bring 'int i' into Scope" "true"
class a {
    void foo () {
        try {
            int i = 0;
        } catch (Exception e) {
            int j = <caret>i;
        }
    }
}