// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    int f(Object o) {
        switch (o) {
            case String s:
                return 2;
            case Double d:
                return 4;
            case Integer i:
                return 0;
            case null, Object obj:
                return 42;
        }
    }
}