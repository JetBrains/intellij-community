// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1:
            case 3:
                throw new IllegalArgumentException("A");
            case 2:
                throw new IllegalStateException("A");
        }
        return "";
    }
}