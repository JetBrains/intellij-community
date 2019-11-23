// "Fix all 'Redundant String operation' problems in file" "true"
class X {
    void test(String s) {
        /*1*/
        /*2*/
        int pos = s/*3*/.indexOf("foo", 10);
        int posBounded = s.substring(10, 20).indexOf("foo");
        int posChar = s.indexOf('f', 10);
        int posIdx = s.substring(10).indexOf('f', 2);
        int posFromZero = s.indexOf("xyzt") > 0;
    }
}