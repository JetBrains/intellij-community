import java.time.*;

public class DateRedundant {
  class First {
    LocalDateTime convert(LocalDateTime source)
    {
      return LocalDateTime.from<caret>(source);
    }

    LocalTime convert(LocalTime source)
    {
      return LocalTime.from(source);
    }

    LocalDate convert(LocalDate source)
    {
      return LocalDate.from(source);
    }

    OffsetDateTime convert(OffsetDateTime source)
    {
      return OffsetDateTime.from(source);
    }

    OffsetTime convert(OffsetTime source)
    {
      return OffsetTime.from(source);
    }

    ZonedDateTime convert(ZonedDateTime source)
    {
      return ZonedDateTime.from(source);
    }
  }


  static class Third {
    private LocalDate getLocalDate(LocalDate fullTime)
    {
      return LocalDate.of(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalDate getLocalDate(ZonedDateTime fullTime)
    {
      return LocalDate.of(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(ZonedDateTime fullTime)
    {
      return LocalTime.of(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }

    private LocalDate getLocalDate(LocalDateTime fullTime)
    {
      return LocalDate.of(fullTime.getYear(),
                          fullTime.getMonth(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalDate getLocalDate2(LocalDateTime fullTime)
    {
      return LocalDate.of(fullTime.getYear(),
                          fullTime.getMonthValue(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(LocalDateTime fullTime)
    {
      return LocalTime.of(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }

    private LocalDate getLocalDate(OffsetDateTime fullTime)
    {
      return LocalDate.of(fullTime.getYear(),
                          fullTime.getMonthValue(),
                          fullTime.getDayOfMonth()
      );
    }

    private LocalTime getLocalTime(OffsetDateTime fullTime)
    {
      return LocalTime.
        of(fullTime.getHour(),
           fullTime.getMinute(),
           fullTime.getSecond(),
           fullTime.getNano());
    }

    private LocalTime getLocalTime(LocalTime fullTime)
    {
      return LocalTime.of(fullTime.getHour(),
                          fullTime.getMinute(),
                          fullTime.getSecond(),
                          fullTime.getNano());
    }
  }


  public static void main(String[] args)
  {
  }
}
