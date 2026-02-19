import java.time.*;

class DateRedundant {
  class First {
    LocalDateTime convert(LocalDateTime source)
    {
      return LocalDateTime.<warning descr="Redundant 'LocalDateTime.from()' call">from<caret></warning>(source);
    }

    LocalTime convert(LocalTime source)
    {
      return LocalTime.<warning descr="Redundant 'LocalTime.from()' call">from</warning>(source);
    }

    LocalDate convert(LocalDate source)
    {
      return LocalDate.<warning descr="Redundant 'LocalDate.from()' call">from</warning>(source);
    }

    OffsetDateTime convert(OffsetDateTime source)
    {
      return OffsetDateTime.<warning descr="Redundant 'OffsetDateTime.from()' call">from</warning>(source);
    }

    OffsetTime convert(OffsetTime source)
    {
      return OffsetTime.<warning descr="Redundant 'OffsetTime.from()' call">from</warning>(source);
    }

    ZonedDateTime convert(ZonedDateTime source)
    {
      return ZonedDateTime.<warning descr="Redundant 'ZonedDateTime.from()' call">from</warning>(source);
    }
  }


  static class Third {
    private LocalDate getLocalDate(LocalDate fullTime)
    {
      return LocalDate.<warning descr="Redundant creation of 'LocalDate' object">of</warning>(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalDate getLocalDate(ZonedDateTime fullTime)
    {
      return LocalDate.<warning descr="Redundant creation of 'LocalDate' object">of</warning>(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(ZonedDateTime fullTime)
    {
      return LocalTime.<warning descr="Redundant creation of 'LocalTime' object">of</warning>(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }

    private LocalDate getLocalDate(LocalDateTime fullTime)
    {
      return LocalDate.<warning descr="Redundant creation of 'LocalDate' object">of</warning>(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalDate getLocalDate2(LocalDateTime fullTime)
    {
      return LocalDate.<warning descr="Redundant creation of 'LocalDate' object">of</warning>(fullTime.getYear(),
                          fullTime.getMonthValue(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(LocalDateTime fullTime)
    {
      return LocalTime.<warning descr="Redundant creation of 'LocalTime' object">of</warning>(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }

    private LocalDate getLocalDate(OffsetDateTime fullTime)
    {
      return LocalDate.<warning descr="Redundant creation of 'LocalDate' object">of</warning>(fullTime.getYear(),
                          fullTime.getMonthValue(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(OffsetDateTime fullTime)
    {
      return LocalTime.
        <warning descr="Redundant creation of 'LocalTime' object">of</warning>(fullTime.getHour(),
           fullTime.getMinute(),
           fullTime.getSecond(),
           fullTime.getNano());
    }

    private LocalTime getLocalTime(LocalTime fullTime)
    {
      return LocalTime.<warning descr="Redundant creation of 'LocalTime' object">of</warning>(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }
  }


  public static void main(String[] args)
  {
  }
}
