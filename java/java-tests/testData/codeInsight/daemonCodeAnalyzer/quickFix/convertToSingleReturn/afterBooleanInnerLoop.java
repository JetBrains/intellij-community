// "Transform body to single exit-point form" "true"
class Test {
    boolean noEmptyStrings(String[][] list) {
        boolean result = true;
        for (String[] inner : list) {
            for (String s : inner) {
                if (s.isEmpty()) {
                    result = false;
                    break;
                }
            }
            if (!result) break;
        }
        return result;
    }
}