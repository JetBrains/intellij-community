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

  long field;

  Optional<Long> getName4() {
    return Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">field</warning>);
  }

  // IDEA-188536
  public void test(Long value1, Long value2, Long value3) {
    // value1 is assumed to be nullable as passed to ofNullable
    long l1 = <warning descr="Unboxing of 'Optional.ofNullable(value1).orElse(null)' may produce 'java.lang.NullPointerException'">Optional.ofNullable(value1).orElse(null)</warning>;
    // value2 is assumed to be notnull as just dereferenced
    long l2 = value2;

    // value3 is assumed to be nullable as passed to ofNullable
    long l3 = Optional.ofNullable(value3).orElse(-1L);
    long l3a = <warning descr="Unboxing of 'value3' may produce 'java.lang.NullPointerException'">value3</warning>;
  }
}

