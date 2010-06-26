// "Bring 'int i' into Scope" "true"
class a {
    void foo (int y) {
        while (y != 0) {
            {
                {
                    final int i = 0;
                }
            }
        }
        int j = <caret>i;
    }
}