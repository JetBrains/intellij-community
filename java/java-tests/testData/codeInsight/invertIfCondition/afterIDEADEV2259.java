// "Invert If Condition" "true"
class K {
    private void bar(int i) {
        switch(i) {
            case 0:
                if (!f(i)) {
                    i = 1;
                    break;
                }
                else {
                    return;
                }
            case 1:
                i = 2;
        }
    }

    private static boolean f(int i) {
        return false;
    }
}
