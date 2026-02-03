// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            /* comment 1 */
            case 1:
                /* comment 2 */
                return "A"; // comment 3
            // comment 1
            case 2:
                // comment 2
                <caret>return "A"; /*comment 3*/
        }
        return "";
    }
}