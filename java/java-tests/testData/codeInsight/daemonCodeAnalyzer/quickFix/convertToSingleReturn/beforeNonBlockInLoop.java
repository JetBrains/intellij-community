// "Transform body to single exit-point form" "true"
class Test {
    String <caret>test2(List<String> list, String foo, String bar) {
        for(String s : list)
            for(int i=0; i<10; i++) {
                if(s.length() == i) return foo;
            }
        return bar;
    }
}