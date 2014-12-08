import org.jetbrains.annotations.Nullable;

class Result {
    private String _message;

    public Result(String _message) {
        this._message = _message;
    }
}

class Main {
    public static Result doIt(String name) {
        Result result;

        Result result1 = newMethod(name);
        if (result1 != null) return result1;

        result = new Result("Name is " + name);
        return result;
    }

    @Nullable
    private static Result newMethod(String name) {
        Result result;
        if (name == null) {
            result = new Result("Name is null");
            return result;
        }
        if (name.length() == 0) {
            result = new Result("Name is empty");
            return result;
        }
        return null;
    }
}