// "Fix all 'Redundant 'String' operation' problems in file" "true"
class X {
    void test(String s, int pos, String s2) {
        if (s.sub<caret>string(pos, pos + 4).equals("xyzt")) { }
        if (s.substring(pos, pos + 5).equals("xyzt")) { }
        if (s.substring(pos, pos + s2.length()).equals(s2)) { }
        if (s.substring(pos, s2.length() + pos).equals(s2)) { }
        if (s.substring(pos, pos + pos).equals(s2)) { }
        if (s.substring(0, 4).equals("xyzt")) { }
        if (s.substring(1, 5).equals("xyzt")) { }
        if (s.substring(1, 1+"xyzt".length()).equals("xyzt")) { }
        if (s.substring(s.length() - 3).equals("...")) {}
        if (s.substring(s.length() - s2.length()).equals(s2)) {}
    }
}