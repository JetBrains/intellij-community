// "Transform body to single exit-point form" "true-preview"
class Test {
    boolean test(String s) {
        boolean result = false;
        if(s != null) {
            if(!s.isEmpty()) {
                System.out.println(s);
                result = true;
            }
        }
        return result;
    }
}