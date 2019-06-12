class Logger {}
class Test {
    public static void main(String[] args) {
        User user = new User();
        Logger logger = null;

        foo<error descr="'foo(T, java.util.logging.Logger, java.util.function.Function<T,java.lang.String>)' in 'Test' cannot be applied to '(User, Logger, <method reference>)'">(user, logger, User::getId)</error>;
    }

    private static <T> void foo(T val, java.util.logging.Logger logger, java.util.function.Function<T, String> idFunction) { }
}

class User {
    private String Id;

    public String getId() {
        return Id;
    }

    public void setId(String id) {
        this.Id = id;
    }
}