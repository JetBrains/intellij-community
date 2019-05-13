class Test {
    private String <warning descr="Field can be converted to a local variable">f</warning> = "";

    void foo () {
      f.substring(0);
    }

}
