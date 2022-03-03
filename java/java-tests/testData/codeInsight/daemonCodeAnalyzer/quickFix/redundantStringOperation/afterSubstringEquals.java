// "Fix all 'Redundant 'String' operation' problems in file" "true"
class X {
    void test(String s, int pos, String s2) {
        if (s.startsWith("xyzt", pos)) { }
        if (s.substring(pos, pos + 5).equals("xyzt")) { }
        if (s.startsWith(s2, pos)) { }
        if (s.startsWith(s2, pos)) { }
        if (s.substring(pos, pos + pos).equals(s2)) { }
        if (s.startsWith("xyzt")) { }
        if (s.startsWith("xyzt", 1)) { }
        if (s.startsWith("xyzt", 1)) { }
        if (s.endsWith("...")) {}
        if (s.endsWith(s2)) {}
    }
}