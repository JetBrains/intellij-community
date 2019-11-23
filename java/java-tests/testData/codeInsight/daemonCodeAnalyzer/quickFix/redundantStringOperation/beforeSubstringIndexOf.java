// "Fix all 'Redundant String operation' problems in file" "true"
class X {
    void test(String s) {
        int pos = s.substr<caret>ing(/*1*/10/*2*/)/*3*/.indexOf("foo");
        int posBounded = s.substring(10, 20).indexOf("foo");
        int posChar = s.substring(10).indexOf('f');
        int posIdx = s.substring(10).indexOf('f', 2);
        int posFromZero = s.substring(0).indexOf("xyzt") > 0;
    }
}