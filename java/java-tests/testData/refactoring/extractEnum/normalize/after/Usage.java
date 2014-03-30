class Usage {
  void foo(EEnum i) {
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
        }
    }

    void foobar() {
        foo(EEnum.FOO);
        foo(EEnum.BAR);
    }
}