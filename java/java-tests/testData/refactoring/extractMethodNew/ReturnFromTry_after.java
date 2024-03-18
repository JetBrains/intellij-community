import org.jetbrains.annotations.NotNull;

class A {
    public String method() {
        try {
            return newMethod();
        }
        catch (Error e) {

        }
        return "";
    }

    private @NotNull String newMethod() {
        try {
            return "";
        }
        finally {
            System.out.println("f");
        }
    }
}