// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(int n) {
        int s = 0;
        for (int i = 0; i < n; i++) {
            switch (i % 4) {
                case 1:
                case 3:
                    s += i;
                    continue;
                case 2:
                    continue;
                default:
                    s += i;
            }
            s /= 2;
        }
        return s;
    }
}