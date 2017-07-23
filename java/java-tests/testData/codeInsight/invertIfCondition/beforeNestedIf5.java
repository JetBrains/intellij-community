// "Invert 'if' condition" "true"
class Main {
    Object foo(boolean b1, boolean b2) {
        if (b1) {
            return null;
        } else if (!b<caret>2) {
            return "a";
        }
        
        return null;
    }
}