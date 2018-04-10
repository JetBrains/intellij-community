// "Move 'return' closer to computation of the value of 's'" "true"
class T {
    String f(String a) {
        String s = a;
        if (s == null) {
            // end of line
            return ""; // return comment
        }
        else if (s.startsWith("@")) {
            /* inline 1 */
            /* inline 2 */
            return s.substring(1); // return comment
        }
        else if (s.startsWith("#")) {
            /* inline */
            return "#"; // return comment
        }
        return s; // return comment
    }
}