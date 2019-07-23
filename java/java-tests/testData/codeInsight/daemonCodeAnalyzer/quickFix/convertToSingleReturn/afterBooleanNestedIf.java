// "Transform body to single exit-point form" "true"
class Test {
    boolean test(String[] arr) {
        boolean result = true;
        if (arr == null) {
            result = false;
        } else {
            String s = arr[0];
            if (s == null) {
                result = false;
            } else {
                s = arr[1];
                if (s == null) {
                    result = false;
                } else if (arr.length > 3) {
                    s = arr[2];
                    if (s != null && s.isEmpty()) {
                        result = false;
                    }
                }
            }
        }
        return result;
    }
}