// "Invert 'if' condition" "true"
class A {
    int test(int x) {
        return switch (x) {
            case 1:
                if (Math.ran<caret>dom() > 0.5) {
                    br
                }
        };
    }
}