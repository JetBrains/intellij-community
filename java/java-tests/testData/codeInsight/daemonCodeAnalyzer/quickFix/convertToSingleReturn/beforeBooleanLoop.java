// "Transform body to single exit-point form" "true-preview"
class Test {
    boolean <caret>hasEmptyString(List<String> list) {
        for (String s : list) {
            if(s.isEmpty()) return true;
        }
        return false;
    }
}