// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void test(int n) {
        String s = switch (n) {
            case 1, 3:
                break "a";
            case 2:
                break "b";
            default:
                break "";
        };
    }
}