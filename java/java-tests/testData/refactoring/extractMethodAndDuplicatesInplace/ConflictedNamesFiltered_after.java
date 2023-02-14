class Test {
    void test() {
        int x = getAnInt();
        System.out.println(x);
    }

    private static int getAnInt() {
        int x = 42;
        return x;
    }

    int getX(){
      return 42;
    }
}