// "Merge with the default 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class Test {
    void foo(String str) {
        switch (str) {
            case "hello":
            case null, default:
                System.out.println(42);
        }
    }
}
