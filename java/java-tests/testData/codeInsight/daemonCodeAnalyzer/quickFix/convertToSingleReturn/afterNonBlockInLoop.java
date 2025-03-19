// "Transform body to single exit-point form" "true-preview"
class Test {
    String test2(List<String> list, String foo, String bar) {
        String result = bar;
        boolean finished = false;
        for(String s : list) {
            for(int i=0; i<10; i++) {
                if(s.length() == i) {
                    result = foo;
                    finished = true;
                    break;
                }
            }
            if (finished) break;
        }
        return result;
    }
}