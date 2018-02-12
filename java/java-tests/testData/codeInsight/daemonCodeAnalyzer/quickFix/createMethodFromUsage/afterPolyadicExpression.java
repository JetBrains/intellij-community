// "Create method 'f'" "true"
class Test {
    {
        long l = f(1) + f(2);
    }

    private long f(int i) {
        return 0;
    }
}