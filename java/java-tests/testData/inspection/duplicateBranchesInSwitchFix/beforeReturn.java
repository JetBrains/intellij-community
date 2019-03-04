// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1:
                return "A";
            case 2:
                return "B";
            case 3:
                <caret>return "A";
        }
        return "";
    }
}