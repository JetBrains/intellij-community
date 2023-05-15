import java.util.*;

class Test {
  Optional<String> getName(List<String> numbers) {
    return Optional.ofNullable(
      numbers.isEmpty() ?
      null :
      numbers.get(0));
  }

  Optional<String> getName2(List<String> numbers) {
    return Optional.ofNullable(
      numbers.isEmpty() ?
      "2" :
      numbers.get(0));
  }

  Optional<String> getName3() {
    return Optional.ofNullable(<warning descr="Passing 'null' argument to 'Optional'">null</warning>);
  }

  long field;

  Optional<Long> getName4() {
    return Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">field</warning>);
  }

  Optional<String> getName5() {
    String s = "";
    return Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">s</warning>);
  }

  Optional<String> testMethodRef(List<String> list) {
    return list.stream().filter(Objects::isNull).map(Optional::<warning descr="Passing 'null' argument to 'Optional'">ofNullable</warning>).findFirst().orElse(Optional.empty());
  }

  Optional<String> testMethodRef2(List<String> list) {
    return list.stream().filter(Objects::nonNull).map(Optional::<warning descr="Passing a non-null argument to 'Optional'">ofNullable</warning>).findFirst().orElse(Optional.empty());
  }
}