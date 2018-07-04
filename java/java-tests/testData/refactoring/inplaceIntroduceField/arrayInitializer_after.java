class Test {

    private String[] str;

    {
        this.str = new String[]{"a", "b", "c"};
        String[] str = this.str;
  }
}