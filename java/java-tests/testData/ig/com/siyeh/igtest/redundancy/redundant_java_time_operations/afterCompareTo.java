// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.time.*;
import java.time.chrono.*;

public class Main {

  public static void main(String[] args) {
    LocalTime localTime = LocalTime.now();
    LocalTime localTime2 = LocalTime.now();
    boolean b = localTime.isAfter(localTime2);
    System.out.println(b);
    b = !localTime.isBefore(localTime2);
    System.out.println(b);
    b = localTime.isBefore(localTime2);
    System.out.println(b);
    b = !localTime.isAfter(localTime2);
    System.out.println(b);
    b = localTime.equals(localTime2);
    System.out.println(b);
    b = !localTime.equals(localTime2);
    System.out.println(b);

    b = localTime.isAfter(localTime2);
    System.out.println(b);
    b = !localTime.isBefore(localTime2);
    System.out.println(b);
    b = localTime.isBefore(localTime2);
    System.out.println(b);
    b = !localTime.isAfter(localTime2);
    System.out.println(b);
    b = localTime.equals(localTime2);
    System.out.println(b);
    b = !localTime.equals(localTime2);
    System.out.println(b);

    b = (!(localTime).equals((localTime2)));
    System.out.println(b);

    OffsetTime offsetTime = OffsetTime.now();
    OffsetTime offsetTime2 = OffsetTime.now();
    b = offsetTime.isAfter(offsetTime2);
    System.out.println(b);
    b = offsetTime.isAfter(offsetTime2);
    System.out.println(b);
    b = !offsetTime.isBefore(offsetTime2);
    System.out.println(b);
    b = offsetTime.isBefore(offsetTime2);
    System.out.println(b);
    b = !offsetTime.isAfter(offsetTime2);
    System.out.println(b);
    b = offsetTime.isEqual(offsetTime2);
    System.out.println(b);
    b = !offsetTime.isEqual(offsetTime2);
    System.out.println(b);

    OffsetDateTime offsetDateTime = OffsetDateTime.now();
    OffsetDateTime offsetDateTime2 = OffsetDateTime.now();

    b = offsetDateTime.isAfter(offsetDateTime2);
    System.out.println(b);
    b = !offsetDateTime.isBefore(offsetDateTime2);
    System.out.println(b);
    b = offsetDateTime.isBefore(offsetDateTime2);
    System.out.println(b);
    b = !offsetDateTime.isAfter(offsetDateTime2);
    System.out.println(b);
    b = offsetDateTime.isEqual(offsetDateTime2);
    System.out.println(b);
    b = !offsetDateTime.isEqual(offsetDateTime2);
    System.out.println(b);

    LocalDate localDate = LocalDate.now();
    LocalDate localDate2 = LocalDate.now();
    b = localDate.isAfter(localDate2);
    System.out.println(b);
    b = !localDate.isBefore(localDate2);
    System.out.println(b);
    b = localDate.isBefore(localDate2);
    System.out.println(b);
    b = !localDate.isAfter(localDate2);
    System.out.println(b);
    b = localDate.isEqual(localDate2);
    System.out.println(b);
    b = !localDate.isEqual(localDate2);
    System.out.println(b);

    b = localDate.compareTo(new ChronoLocalDate() {}) > 0;

    LocalDateTime localDateTime = LocalDateTime.now();
    LocalDateTime localDateTime2 = LocalDateTime.now();
    b = localDateTime.isAfter(localDateTime2);
    System.out.println(b);
    b = !localDateTime.isBefore(localDateTime2);
    System.out.println(b);
    b = localDateTime.isBefore(localDateTime2);
    System.out.println(b);
    b = !localDateTime.isAfter(localDateTime2);
    System.out.println(b);
    b = localDateTime.isEqual(localDateTime2);
    System.out.println(b);
    b = !localDateTime.isEqual(localDateTime2);
    System.out.println(b);
      /*test1*/
      /*test2*/
      /*test3*/
      /*test4*/
      /*test5*/
      b = /*test0*/localDateTime.isAfter(localDateTime2);
    System.out.println(b);
    try {
      b = localDateTime.compareTo(new ChronoLocalDateTime<>() {}) > 0;
    }
    catch (Exception e) {

    }

      /*test1*/
      /*test2*/
      /*test3*/
      /*test4*/
      /*test5*/
      b = /*test0*/localDateTime.isAfter(localDateTime2);
  }

  public static boolean unresolvedType(LocalTime localTime) {
    return localTime.compareTo(unresolved) > 0;
  }
}