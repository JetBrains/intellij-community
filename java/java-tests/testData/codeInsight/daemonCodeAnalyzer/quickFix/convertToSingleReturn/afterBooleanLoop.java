// "Transform body to single exit-point form" "true"
class Test {
    boolean hasEmptyString(List<String> list) {
        boolean result = false;
        for (String s : list) {
            if (s.isEmpty()) {
                result = true;
                break;
            }
        }
        return result;
    }
}