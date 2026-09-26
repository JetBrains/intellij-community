// "Bring 'int i' into scope" "true-preview"
class a {
    void foo (int y) {
        int i = 0;
        while (y != 0) {
            {
                {
                    i = 0;
                }
            }
        }
        int j = i;
    }
}