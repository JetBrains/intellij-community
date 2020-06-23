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

    @NotNull
    private String newMethod() {
        try {
            return "";
        }
        finally {
            System.out.println("f");
        }
    }
}