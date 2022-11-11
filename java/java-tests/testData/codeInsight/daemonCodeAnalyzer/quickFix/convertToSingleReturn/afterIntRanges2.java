// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String[] strings) {
        int result = -1;
        for (String string : strings) {
            if (!string.equal("foo")) {
                result = string.length();
                break;// non-negative number
            }
        }
        if (result == -1) {
            result = strings.length;
        }
        return result;
    }
}