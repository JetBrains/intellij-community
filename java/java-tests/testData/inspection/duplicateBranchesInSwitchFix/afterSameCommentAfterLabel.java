// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1:
            case 2:
                /* comment 1 */
                return "A";
        }
        return "";
    }
}