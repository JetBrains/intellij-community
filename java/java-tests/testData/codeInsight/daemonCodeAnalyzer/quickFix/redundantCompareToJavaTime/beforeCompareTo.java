// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.time.*;
import java.time.chrono.*;

public class Main {

  public static void main(String[] args) {
    LocalTime localTime = LocalTime.now();
    LocalTime localTime2 = LocalTime.now();
    boolean b = localTime.com<caret>pareTo(localTime2) > 0;
    System.out.println(b);
    b = localTime.compareTo(localTime2) >= 0;
    System.out.println(b);
    b = localTime.compareTo(localTime2) < 0;
    System.out.println(b);
    b = localTime.compareTo(localTime2) <= 0;
    System.out.println(b);
    b = localTime.compareTo(localTime2) == 0;
    System.out.println(b);
    b = localTime.compareTo(localTime2) != 0;
    System.out.println(b);

    b = 0 < localTime.compareTo(localTime2);
    System.out.println(b);
    b = 0 <= localTime.compareTo(localTime2);
    System.out.println(b);
    b = 0 > localTime.compareTo(localTime2);
    System.out.println(b);
    b = 0 >= localTime.compareTo(localTime2);
    System.out.println(b);
    b = 0 == localTime.compareTo(localTime2);
    System.out.println(b);
    b = 0 != localTime.compareTo(localTime2);
    System.out.println(b);

    b = ((localTime).compareTo((localTime2)) != 0);
    System.out.println(b);

    OffsetTime offsetTime = OffsetTime.now();
    OffsetTime offsetTime2 = OffsetTime.now();
    b = offsetTime.compareTo(offsetTime2) > 0;
    System.out.println(b);
    b = (((offsetTime.compareTo(offsetTime2)))) > 0;
    System.out.println(b);
    b = offsetTime.compareTo(offsetTime2) >= 0;
    System.out.println(b);
    b = offsetTime.compareTo(offsetTime2) < 0;
    System.out.println(b);
    b = offsetTime.compareTo(offsetTime2) <= 0;
    System.out.println(b);
    b = offsetTime.compareTo(offsetTime2) == 0;
    System.out.println(b);
    b = offsetTime.compareTo(offsetTime2) != 0;
    System.out.println(b);

    OffsetDateTime offsetDateTime = OffsetDateTime.now();
    OffsetDateTime offsetDateTime2 = OffsetDateTime.now();

    b = offsetDateTime.compareTo(offsetDateTime2) > 0;
    System.out.println(b);
    b = offsetDateTime.compareTo(offsetDateTime2) >= 0;
    System.out.println(b);
    b = offsetDateTime.compareTo(offsetDateTime2) < 0;
    System.out.println(b);
    b = offsetDateTime.compareTo(offsetDateTime2) <= 0;
    System.out.println(b);
    b = offsetDateTime.compareTo(offsetDateTime2) == 0;
    System.out.println(b);
    b = offsetDateTime.compareTo(offsetDateTime2) != 0;
    System.out.println(b);

    LocalDate localDate = LocalDate.now();
    LocalDate localDate2 = LocalDate.now();
    b = localDate.compareTo(localDate2) > 0;
    System.out.println(b);
    b = localDate.compareTo(localDate2) >= 0;
    System.out.println(b);
    b = localDate.compareTo(localDate2) < 0;
    System.out.println(b);
    b = localDate.compareTo(localDate2) <= 0;
    System.out.println(b);
    b = localDate.compareTo(localDate2) == 0;
    System.out.println(b);
    b = localDate.compareTo(localDate2) != 0;
    System.out.println(b);

    b = localDate.compareTo(new ChronoLocalDate() {}) > 0;

    LocalDateTime localDateTime = LocalDateTime.now();
    LocalDateTime localDateTime2 = LocalDateTime.now();
    b = localDateTime.compareTo(localDateTime2) > 0;
    System.out.println(b);
    b = localDateTime.compareTo(localDateTime2) >= 0;
    System.out.println(b);
    b = localDateTime.compareTo(localDateTime2) < 0;
    System.out.println(b);
    b = localDateTime.compareTo(localDateTime2) <= 0;
    System.out.println(b);
    b = localDateTime.compareTo(localDateTime2) == 0;
    System.out.println(b);
    b = localDateTime.compareTo(localDateTime2) != 0;
    System.out.println(b);
    b = /*test0*/localDateTime/*test1*/./*test2*/compareTo/*test3*/(/*test4*/localDateTime2/*test5*/) > 0;
    System.out.println(b);
    try {
      b = localDateTime.compareTo(new ChronoLocalDateTime<>() {}) > 0;
    }
    catch (Exception e) {

    }

    b = /*test0*/localDateTime/*test1*/./*test2*/compareTo/*test3*/(/*test4*/localDateTime2/*test5*/) > 0;
  }
}