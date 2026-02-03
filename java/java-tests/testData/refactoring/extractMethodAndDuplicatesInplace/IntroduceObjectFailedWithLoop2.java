public class Test {

  public void validate(ArrayList<Supplier<Integer>> rules){
    ArrayList<String> strings = null;
    ArrayList<Integer> integers = null;

    for (Supplier<Integer> rule : rules) {
      <selection>int t = rule.get();
      if (t == 1000) {
        if (strings == null) {
          strings = new ArrayList<>();
        }
        strings.add("q");
      } else if (t == 2 || t == 3) {
        if (integers == null) {
          integers = new ArrayList<>();
        }
        integers.add(1);
      }</selection>
    }
  }
}