import java.time.*;

class DateRedundant {
  class First {
    LocalDateTime convert(LocalDateTime source)
    {
      return source<caret>;
    }

    LocalTime convert(LocalTime source)
    {
      return source;
    }

    LocalDate convert(LocalDate source)
    {
      return source;
    }

    OffsetDateTime convert(OffsetDateTime source)
    {
      return source;
    }

    OffsetTime convert(OffsetTime source)
    {
      return source;
    }

    ZonedDateTime convert(ZonedDateTime source)
    {
      return source;
    }
  }


  static class Third {
    private LocalDate getLocalDate(LocalDate fullTime)
    {
      return fullTime;
    }

    private LocalDate getLocalDate(ZonedDateTime fullTime)
    {
      return fullTime.toLocalDate();
    }

    private LocalTime getLocalTime(ZonedDateTime fullTime)
    {
      return fullTime.toLocalTime();
    }

    private LocalDate getLocalDate(LocalDateTime fullTime)
    {
      return fullTime.toLocalDate();
    }

    private LocalDate getLocalDate2(LocalDateTime fullTime)
    {
      return fullTime.toLocalDate();
    }

    private LocalTime getLocalTime(LocalDateTime fullTime)
    {
      return fullTime.toLocalTime();
    }

    private LocalDate getLocalDate(OffsetDateTime fullTime)
    {
      return fullTime.toLocalDate();
    }

    private LocalTime getLocalTime(OffsetDateTime fullTime)
    {
      return fullTime.toLocalTime();
    }

    private LocalTime getLocalTime(LocalTime fullTime)
    {
      return fullTime;
    }
  }


  public static void main(String[] args)
  {
  }
}
