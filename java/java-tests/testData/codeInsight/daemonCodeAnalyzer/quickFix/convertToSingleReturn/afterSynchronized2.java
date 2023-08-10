// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(int x) {
        String result = null;
        synchronized(this) {
            if(x == 0) {
                result = "foo";
            } else if(x == 1) {
                result = "bar";
            }
        }
        if (result == null) {
            result = "baz";
        }
        return result;
    }
}