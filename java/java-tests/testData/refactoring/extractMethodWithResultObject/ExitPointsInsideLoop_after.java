class s {
    void f(boolean b) {
        for (;;) {
            NewMethodResult x = newMethod(b);
            if (x.exitKey == 1) break;
        }
    }

    NewMethodResult newMethod(boolean b) {
        if (b) {
            return new NewMethodResult((1 /* exit key */));
        }
        return new NewMethodResult((-1 /* exit key */));
    }

    static class NewMethodResult {
        private int exitKey;

        public NewMethodResult(int exitKey) {
            this.exitKey = exitKey;
        }
    }
}