
class Bug {
    void m() {
        new Bug() {
            public void m1(String s) {
                return s.substring(1);
            }
            
            public void m2(String s) {
                String str = s.substring(1);
                return str;
            }
        };
    }
}