// "Bring 'int i' into scope" "true-preview"
class a {
    void foo (int y) {
        while (y != 0) {
            {
                {
                    final int i = 0;
                }
            }
        }
        <caret>i = 0;
    }
}