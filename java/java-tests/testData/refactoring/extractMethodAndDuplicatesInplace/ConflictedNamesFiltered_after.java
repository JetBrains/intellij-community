class Test {
    void test() {
        int x = getInt();
        System.out.println(x);
    }

    private static int getInt() {
        int x = 42;
        return x;
    }

    int getX(){
      return 42;
    }
}