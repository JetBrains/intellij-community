// "Merge with 'case 1'" "GENERIC_ERROR_OR_WARNING"
class C {
    int foo(int n, boolean b) {
        switch (n) {
            case 1, 3 -> {
                if(b) {
                    return bar("A");
                }
            }
            case 2 -> {
                if(b) {
                    return bar("B");
                }
            }
        }
        return 0;
    }
    int bar(String s){return s.charAt(0);}
}