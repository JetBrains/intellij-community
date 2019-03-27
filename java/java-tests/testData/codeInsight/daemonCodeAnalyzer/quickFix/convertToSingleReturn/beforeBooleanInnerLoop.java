// "Transform body to single exit-point form" "true"
class Test {
    boolean <caret>noEmptyStrings(String[][] list) {
        for (String[] inner : list) {
            for (String s : inner) {
                if (s.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}