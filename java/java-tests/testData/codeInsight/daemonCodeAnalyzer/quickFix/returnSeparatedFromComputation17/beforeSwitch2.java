// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(Object o) {
        int n = -1;
        switch (o) {
            case String s:
                n = 2;
                break;
            case Double d:
                n = 4;
                break;
            case Integer i:
                n = 0;
                break;
            case null, Object obj:
                n = 42;
        }
        re<caret>turn n;
    }
}