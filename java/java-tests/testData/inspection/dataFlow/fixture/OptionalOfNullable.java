import java.util.List;
import java.util.Optional;

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

}

