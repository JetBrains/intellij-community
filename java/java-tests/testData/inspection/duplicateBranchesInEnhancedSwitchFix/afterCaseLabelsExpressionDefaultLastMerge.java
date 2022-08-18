// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void test(int n) {
        String s = switch (n) {
            case 2:
                break "b";
            case 3, 1:
            default:
                break "a";
        };
    }
}