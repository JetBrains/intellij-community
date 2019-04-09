import java.util.ArrayList;

class C {
    public Object m() {
        NewMethodResult x = newMethod();
        if (x.exitKey == 1) return x.returnResult;
        return null;
    }

    NewMethodResult newMethod() {
        for (Object o : new ArrayList<Object>()) {
            if (o != null) {
                return new NewMethodResult((1 /* exit key */), o);
            }
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private Object returnResult;

        public NewMethodResult(int exitKey, Object returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}