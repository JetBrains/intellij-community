class HashMap {}
class Test {
    public static void main(String[] args) {
        User user = new User();
        HashMap hashMap = null;

        foo<error descr="'foo(T, java.util.HashMap, java.util.function.Function<T,java.lang.String>)' in 'Test' cannot be applied to '(User, HashMap, <method reference>)'">(user, hashMap, User::getId)</error>;
    }

    private static <T> void foo(T val, java.util.HashMap hashMap, java.util.function.Function<T, String> idFunction) { }
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