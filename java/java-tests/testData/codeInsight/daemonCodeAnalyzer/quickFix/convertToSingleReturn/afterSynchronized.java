// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(int x) {
        String result;
        synchronized(this) {
            if(x == 0) {
                result = "foo";
            } else if(x == 1) {
                result = "bar";
            } else {
                result = "baz";
            }
        }
        return result;
    }
}