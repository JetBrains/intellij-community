import java.time.*;

class Main {

  LocalTime convert(LocalTime source)
  {
    return LocalTime.<warning descr="Redundant 'LocalTime.from()' call">from<caret></warning>(source);
  }
}
