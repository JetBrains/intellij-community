// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1:
            case 3:
                return "A";
            case 2:
                return "B";
        }
        return "";
    }
}