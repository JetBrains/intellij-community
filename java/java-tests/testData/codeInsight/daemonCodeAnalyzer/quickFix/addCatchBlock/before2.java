// "Add Catch Clause(s)" "false"
// should not try to add catch clauses across method boundaries
class s {
    int f() throws Exception {
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            <caret>int i = f();//
                        }
                    };
                } catch (Exception e) {
                }
            }
        };
        return 0;
    }
}

