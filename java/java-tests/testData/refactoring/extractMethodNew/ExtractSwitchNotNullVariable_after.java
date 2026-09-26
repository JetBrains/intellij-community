import org.jetbrains.annotations.NotNull;

class Test {
    void test(int y){
        String x = newMethod(y);
        System.out.println(x);
    }

    private @NotNull String newMethod(int y) {
        String x;
        switch (y){
            case 3:
                x = "1";
                break;
            case 5:
                x = "2";
                break;
            default:
                throw new IllegalArgumentException();
        }
        return x;
    }
}