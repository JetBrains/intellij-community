// "Invert 'if' condition" "true"
class Main {
    boolean method(boolean a, boolean b) {
        for (int i = 1; i < 10; i++)
            if (a)
                <caret>if (!b)   /* comment 1 */
                    return true; /* comment 2 */
        return false;            /* comment 3 */
    }
}