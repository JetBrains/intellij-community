// "Transform body to single exit-point form" "true-preview"
class Test {
    boolean test(String[] arr) {
        boolean result = false;
        boolean finished = false;
        if (arr != null) {
            System.out.println("ok");
            for(String s : arr) {
                if (s.isEmpty()) {
                    finished = true;
                    break;
                }
                System.out.println(s);
            }
            if (!finished) {
                result = true;
            }
        }
        return result;
    }
}