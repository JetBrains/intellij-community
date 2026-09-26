class Result {
    private String _message;

    public Result(String _message) {
        this._message = _message;
    }
}

class Main {
    public static Result doIt(String name) {
        Result result;

        <selection>if (name == null) {
            result = new Result("Name is null");
            return result;
        }
        if (name.length() == 0) {
            result = new Result("Name is empty");
            return result;
        }</selection>

        result = new Result("Name is " + name);
        return result;
    }
}