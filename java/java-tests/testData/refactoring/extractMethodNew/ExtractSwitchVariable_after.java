import org.jetbrains.annotations.Nullable;

class Test {
    void test(int y){
        Integer x = newMethod(y);
        if (x == null) return;
        System.out.println(x);
    }

    private @Nullable Integer newMethod(int y) {
        int x;
        switch (y){
            case 3:
                x = 1;
                break;
            case 5:
                x = 2;
                break;
            default:
                return null;
        }
        return x;
    }
}