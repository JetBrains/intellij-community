import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  void foo(final Stream<Person> stream) {
    final Map<String,List<Person>> mapByFirstLetter = stream.collect(Collectors.groupingBy(p -> "" + p.name.charAt(0)));

    final String vV = mapByFirstLetter.values().stream().map(lp -> lp.stream().map(p -> p.name)
      .collect(Collectors.joining("/","<",">"))) .collect(Collectors.joining(" : "));

    final String vV2 = mapByFirstLetter.values().stream()
      .map(lp -> lp.stream().map(Person::getName).collect(Collectors.joining("/","<",">")))
      .collect(Collectors.joining(" : "));
    System.out.println("mapByFirstLetter2 :   "+ vV2);
  }

  public static class Person {
    private String name;
    public Person(String name) {
      this.name = name;
    }
    public String getName() {return name;}
  }
}
