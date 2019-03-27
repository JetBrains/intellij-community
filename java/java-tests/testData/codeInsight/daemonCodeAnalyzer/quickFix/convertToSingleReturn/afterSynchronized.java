// "Transform body to single exit-point form" "true"
class Test {
    String test(int x) {
        String result = "foo";
        synchronized (this) {
            if (x != 0) {
                if (x == 1) {
                    result = "bar";
                } else {
                    result = "baz";
                }
            }
        }
        return result;
    }
}