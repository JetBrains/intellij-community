class C {
  {
      newMethod();
  }

  void f() {
      newMethod();
  }

    private void newMethod() {
        final int j = 0;
        System.out.println(j);
    }
}