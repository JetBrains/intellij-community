// "Transform body to single exit-point form" "true-preview"
class Test {
    String test2(List<String> list, String foo, String bar) {
        String result = foo;
        boolean finished = false;
        for(String s : list) {
            for(int i=0; i<10; i++) {
                bar = s;
                if(s.length() == i) {
                    finished = true;
                    break;
                }
            }
            if (finished) break;
        }
        if (!finished) {
            result = bar;
        }
        return result;
    }
}