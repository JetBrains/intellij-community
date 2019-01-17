// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(int n, boolean b) {
        switch (n) {
            case 1:
                if(b) {
                    return bar("A");
                } else {
                    break;
                }
            case 2:
                if(b) {
                    return bar("B");
                } else {
                    break;
                }
            case 3:
                if(b) {
                    return bar("A");
                } else {
                    break;
                <caret>}
        }
        return 0;
    }
    int bar(String s){return s.charAt(0);}
}