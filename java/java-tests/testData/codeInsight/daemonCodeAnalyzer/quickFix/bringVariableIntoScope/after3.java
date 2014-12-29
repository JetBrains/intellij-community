// "Bring 'int i' into scope" "true"
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