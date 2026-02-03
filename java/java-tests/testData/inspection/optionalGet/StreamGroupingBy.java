import java.util.*;
import java.util.stream.*;

class TestClassContainingOptional {

  void test() {
    List<TestClassContainingOptional> list = new ArrayList<>();
    list.add(new TestClassContainingOptional("name1", "optional"));
    list.add(new TestClassContainingOptional("name2"));
    System.out.println(list.stream()
                         .filter(e -> e.getOptionalString().isPresent())
                         .collect(Collectors.groupingBy(e -> e.getOptionalString().get()))
    );
    System.out.println(list.stream()
                         .filter(e -> e.getOptionalString().isPresent())
                         .collect(Collectors.groupingBy(e -> e.getOptionalString().get(), Collectors.toSet()))
    );
    TreeMap<Object, Set<Object>> collect = list.stream()
      .filter(e -> e.getOptionalString().isPresent())
      .collect(Collectors.groupingBy(e -> e.getOptionalString().get(), TreeMap::new, Collectors.toSet()));
    System.out.println(collect);
  }

  String name;
  String optionalString = null;


  public TestClassContainingOptional(String name) {
    this.name = name;
  }

  public TestClassContainingOptional(String name, String optionalString) {
    this.name = name;
    this.optionalString = optionalString;
  }

  public Optional<String> getOptionalString() {
    return Optional.ofNullable(optionalString);
  }
}