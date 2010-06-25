// "Bring 'int i' into Scope" "true"
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