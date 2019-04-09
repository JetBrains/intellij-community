class Result {
    private String _message;

    public Result(String _message) {
        this._message = _message;
    }
}

class Main {
    public static Result doIt(String name) {
        Result result;

        NewMethodResult x = newMethod(name);
        if (x.exitKey == 1) return x.returnResult;

        result = new Result("Name is " + name);
        return result;
    }

    static NewMethodResult newMethod(String name) {
        Result result;
        if (name == null) {
            result = new Result("Name is null");
            return new NewMethodResult((1 /* exit key */), result, (null /* missing value */));
        }
        if (name.length() == 0) {
            result = new Result("Name is empty");
            return new NewMethodResult((1 /* exit key */), result, (null /* missing value */));
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private Result returnResult;
        private Result result;

        public NewMethodResult(int exitKey, Result returnResult, Result result) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
            this.result = result;
        }
    }
}