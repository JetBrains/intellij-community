// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1:
                throw new IllegalArgumentException("A");
            case 2:
                throw new IllegalStateException("A");
            case 3:
                <caret>throw new IllegalArgumentException("A");
        }
        return "";
    }
}