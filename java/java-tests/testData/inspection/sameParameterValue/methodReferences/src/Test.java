public class Test {


  public void foo() {
    bar(null);

    List<String> strings = Collections.singletonList("NotNull!");
    strings.forEach(this::bar);
  }

  private void bar(@Nullable String goo) {
    System.out.println(goo);
  }
}