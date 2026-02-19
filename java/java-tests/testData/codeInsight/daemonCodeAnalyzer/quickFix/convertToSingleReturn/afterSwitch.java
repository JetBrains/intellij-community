// "Transform body to single exit-point form" "true-preview"
class Test {
    String test(int x) {
        String result;
        switch (x) {
            case 1:
                result = "foo";
                break;
            case 2:
                result = "bar";
                break;
            case 3:
                result = "baz";
                break;
            default:
                result = "qux";
                break;
        }
        return result;
    }
}