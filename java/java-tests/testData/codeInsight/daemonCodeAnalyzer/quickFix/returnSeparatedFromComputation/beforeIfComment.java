// "Move 'return' closer to computation of the value of 's'" "true-preview"
class T {
    String f(String a) {
        String s = a;
        if (s == null) {
            s = ""; // end of line
        }
        else if (s.startsWith("@")) {
            s = /* inline 1 */ s.substring(1); /* inline 2 */
        }
        else if (s.startsWith("#")) {
            s = "#"; /* inline */
        }
        ret<caret>urn (s); // return comment
    }
}