// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;

class Main {

  public static int test(OffsetDateTime offsetDateTime) {
    return offsetDateTime.get<caret>(ChronoField.NANO_OF_SECOND);
  }
  public static int test2(OffsetDateTime offsetDateTime) {
    return offsetDateTime.get(ChronoField.INSTANT_SECONDS);
  }
  public static int test3(OffsetDateTime offsetDateTime) {
    return offsetDateTime.get(ChronoField.YEAR);
  }
  public static void test4(OffsetTime time) {
    time.get(1, ChronoUnit.YEARS);
  }
}
