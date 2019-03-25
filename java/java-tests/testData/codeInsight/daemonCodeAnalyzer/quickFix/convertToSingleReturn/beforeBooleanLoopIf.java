// "Transform body to single exit-point form" "true"
class Test {
    boolean <caret>test(String[] arr) {
        if (arr != null) {
            System.out.println("ok");
            for(String s : arr) {
                if (s.isEmpty()) return false;
                System.out.println(s);
            }
            return true;
        }
        return false;
    }
}