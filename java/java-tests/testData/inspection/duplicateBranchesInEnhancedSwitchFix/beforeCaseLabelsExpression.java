// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void test(int n) {
        String s = switch (n) {
            case 1:
                break "a";
            case 2:
                break "b";
            case 3:
                <caret>break "a";
            default:
                break "";
        };
    }
}