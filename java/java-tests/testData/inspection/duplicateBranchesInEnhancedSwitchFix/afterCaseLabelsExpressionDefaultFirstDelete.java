// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void test(int n) {
        String s = switch (n) {
            default:
            case 1:
                break "a";
            case 2:
                break "b";
        };
    }
}