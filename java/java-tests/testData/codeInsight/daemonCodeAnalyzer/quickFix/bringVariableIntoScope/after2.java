// "Bring 'int i' into scope" "true"
class a {
    void foo (int y) {
        int i;
        while (y != 0) {
            {
                {
                    i = 0;
                }
            }
        }
        <caret>i = 0;
    }
}