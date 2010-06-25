// "Invert If Condition" "true"
class K {
    private void bar(int i) {
        switch(i) {
            case 0:
                <caret>if (f(i)) {
                    return;
                }
                i = 1;
                break;
            case 1:
                i = 2;
        }
    }

    private static boolean f(int i) {
        return false;
    }
}
