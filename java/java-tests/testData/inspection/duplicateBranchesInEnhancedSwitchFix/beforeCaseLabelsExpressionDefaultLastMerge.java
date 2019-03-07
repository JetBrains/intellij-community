// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void test(int n) {
        String s = switch (n) {
            case 1:
                <caret>break "a";
            case 2:
                break "b";
            case 3:
            default:
                break "a";
        };
    }
}