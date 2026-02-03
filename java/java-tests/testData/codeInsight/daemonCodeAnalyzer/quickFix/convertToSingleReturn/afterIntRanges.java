// "Transform body to single exit-point form" "true-preview"
class Test {
    int test(String[] strings) {
        int result = 0;
        for (String string : strings) {
            if (!string.isEmpty()) {
                result = string.length();
                break;// positive number
            }
        }
        if (result == 0) {
            result = strings.length;
        }
        return result;
    }
}