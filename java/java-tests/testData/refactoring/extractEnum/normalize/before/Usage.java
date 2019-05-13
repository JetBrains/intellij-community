class Usage {
  void foo(int i) {
        switch (i) {
            case Test.FOO:
                break;
            case Test.BAR:
                break;
        }
    }

    void foobar() {
        foo(Test.FOO);
        foo(Test.BAR);
    }
}