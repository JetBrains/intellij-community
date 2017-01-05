// "Move 'return' closer to computation of the value of 's'" "true"
class T {
    String f(String a) {
        String s = a;
        if (s == null) {
            return ""; // return comment
            // end of line
        }
        else if (s.startsWith("@")) {
            return s.substring(1); // return comment
    /* inline 1 *//* inline 2 */
        }
        else if (s.startsWith("#")) {
            return "#"; // return comment
    /* inline */
        }
        return s; // return comment
    }
}