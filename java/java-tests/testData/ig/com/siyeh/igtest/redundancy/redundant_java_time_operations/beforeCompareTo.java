// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.time.*;
import java.time.chrono.*;

public class Main {

  public static void main(String[] args) {
    LocalTime localTime = LocalTime.now();
    LocalTime localTime2 = LocalTime.now();
    boolean b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo<caret></warning>(localTime2) > 0;
    System.out.println(b);
    b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2) >= 0;
    System.out.println(b);
    b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2) < 0;
    System.out.println(b);
    b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2) <= 0;
    System.out.println(b);
    b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2) == 0;
    System.out.println(b);
    b = localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2) != 0;
    System.out.println(b);

    b = 0 < localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);
    b = 0 <= localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);
    b = 0 > localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);
    b = 0 >= localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);
    b = 0 == localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);
    b = 0 != localTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localTime2);
    System.out.println(b);

    b = ((localTime).<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>((localTime2)) != 0);
    System.out.println(b);

    OffsetTime offsetTime = OffsetTime.now();
    OffsetTime offsetTime2 = OffsetTime.now();
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) > 0;
    System.out.println(b);
    b = (((offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2)))) > 0;
    System.out.println(b);
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) >= 0;
    System.out.println(b);
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) < 0;
    System.out.println(b);
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) <= 0;
    System.out.println(b);
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) == 0;
    System.out.println(b);
    b = offsetTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetTime2) != 0;
    System.out.println(b);

    OffsetDateTime offsetDateTime = OffsetDateTime.now();
    OffsetDateTime offsetDateTime2 = OffsetDateTime.now();

    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) > 0;
    System.out.println(b);
    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) >= 0;
    System.out.println(b);
    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) < 0;
    System.out.println(b);
    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) <= 0;
    System.out.println(b);
    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) == 0;
    System.out.println(b);
    b = offsetDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(offsetDateTime2) != 0;
    System.out.println(b);

    LocalDate localDate = LocalDate.now();
    LocalDate localDate2 = LocalDate.now();
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) > 0;
    System.out.println(b);
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) >= 0;
    System.out.println(b);
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) < 0;
    System.out.println(b);
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) <= 0;
    System.out.println(b);
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) == 0;
    System.out.println(b);
    b = localDate.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDate2) != 0;
    System.out.println(b);

    b = localDate.compareTo(new <error descr="Class 'Anonymous class derived from ChronoLocalDate' must implement abstract method 'getChronology()' in 'ChronoLocalDate'">ChronoLocalDate</error>() {}) > 0;

    LocalDateTime localDateTime = LocalDateTime.now();
    LocalDateTime localDateTime2 = LocalDateTime.now();
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) > 0;
    System.out.println(b);
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) >= 0;
    System.out.println(b);
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) < 0;
    System.out.println(b);
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) <= 0;
    System.out.println(b);
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) == 0;
    System.out.println(b);
    b = localDateTime.<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>(localDateTime2) != 0;
    System.out.println(b);
    b = /*test0*/localDateTime/*test1*/./*test2*/<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>/*test3*/(/*test4*/localDateTime2/*test5*/) > 0;
    System.out.println(b);
    try {
      b = localDateTime.compareTo(new <error descr="Class 'Anonymous class derived from ChronoLocalDateTime' must implement abstract method 'toLocalDate()' in 'ChronoLocalDateTime'">ChronoLocalDateTime<></error>() {}) > 0;
    }
    catch (Exception e) {

    }

    b = /*test0*/localDateTime/*test1*/./*test2*/<warning descr="Expression with 'java.time' 'compareTo()' call can be simplified">compareTo</warning>/*test3*/(/*test4*/localDateTime2/*test5*/) > 0;
  }

  public static boolean unresolvedType(LocalTime localTime) {
    return localTime.compareTo(<error descr="Cannot resolve symbol 'unresolved'">unresolved</error>) > 0;
  }
}