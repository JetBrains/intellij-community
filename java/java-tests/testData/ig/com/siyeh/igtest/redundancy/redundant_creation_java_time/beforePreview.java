import java.time.*;

class Main {

  LocalTime convert(LocalTime source)
  {
    return LocalTime.from<caret>(source);
  }
}
