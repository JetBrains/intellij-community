// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n, boolean b) {
        String s = switch (n) {
            case 1 -> {
                if (b) {
                    break bar("A");
                }
                else {
                    bar("z");
                }
                break bar("o");
            }
            case 2 -> {
                if (b) {
                    break bar("B");
                }
                else {
                    bar("z");
                }
                break bar("o");
            }
            case 3 -> {
                if (b) {<caret>
                    break bar("A");
                }
                else {
                    bar("z");
                }
                break bar("o");
            }
            default -> "";
        };
    }
    String bar(String s){}
}