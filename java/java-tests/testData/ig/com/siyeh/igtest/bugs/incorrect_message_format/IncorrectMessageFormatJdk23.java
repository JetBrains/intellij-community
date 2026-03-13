import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class IncorrectMessageFormatJdk23 {
  static void main() {
    new IncorrectMessageFormatJdk23().testJdk23FormatTypes();
  }

  void testJdk23FormatTypes() {
    // JDK 23+ format types — no warnings expected
    String r;
    r = MessageFormat.format("{0, dtf_date, yy}", LocalDateTime.now());
    r = MessageFormat.format("{0, dtf_time, ss}", LocalDateTime.now());
    r = MessageFormat.format("{0, dtf_datetime, yy}", LocalDateTime.now());
    r = MessageFormat.format("{0, list}", List.of("1"));
    r = MessageFormat.format("{0, BASIC_ISO_DATE}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_LOCAL_DATE}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_OFFSET_DATE}", OffsetDateTime.now());
    r = MessageFormat.format("{0, ISO_DATE}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_LOCAL_TIME}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_OFFSET_TIME}", OffsetDateTime.now());
    r = MessageFormat.format("{0, ISO_TIME}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_LOCAL_DATE_TIME}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_OFFSET_DATE_TIME}", OffsetDateTime.now());
    r = MessageFormat.format("{0, ISO_ZONED_DATE_TIME}", ZonedDateTime.now());
    r = MessageFormat.format("{0, ISO_DATE_TIME}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_ORDINAL_DATE}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_WEEK_DATE}", LocalDateTime.now());
    r = MessageFormat.format("{0, ISO_INSTANT}", Instant.now());
    r = MessageFormat.format("{0, RFC_1123_DATE_TIME}", OffsetDateTime.now());

    r = MessageFormat.format("{0, number}", 42);
    r = MessageFormat.format("{0, date}", new java.util.Date());
    r = MessageFormat.format("{0, time}", new java.util.Date());

    // Unknown type — still a warning
    r = MessageFormat.format("{0,<warning descr="Unknown format type 'totally_unknown'">totally_unknown</warning>}", 1);
    r = MessageFormat.format("{0,<warning descr="Unknown format type ' or'"> or</warning>}", "a", "b");
    r = MessageFormat.format("{0,<warning descr="Unknown format type ' unit'"> unit</warning>}", 42);
  }
}
