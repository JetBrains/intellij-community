import java.util.*;

class MyTest {

  static Optional<BulkResult> map(BulkResultType bulkResultType) {

    final LocalDateTime documentDateTime = Optional.ofNullable(bulkResultType)
      .map(BulkResultType::getObservationBulkmailHeader)
      .map(HeaderType::getDocumentDateTime)
      .orElseThrow(() -> new InvalidMessageException(""));


    final Optional<LocalDateTime> reportedDateTime = Optional.ofNullable(bulkResultType)
      .map(BulkResultType::getObservationBulkmailHeader)
      .map(bulkResultHeaderType::getResultReleaseTime)
      .map(ResultReleaseTimeType::getReportedDateTime);


    final Optional<LocalDateTime> lastModificationDateTime = Optional.ofNullable(bulkResultType)
      .map(BulkResultType::getObservationBulkmailHeader)
      .map(HeaderType::getLastModificationDateTime);
    final Optional<String> statusCode =Optional.ofNullable(bulkResultType)
      .map(BulkResultType::getObservationBulkmailHeader)
      .map(bulkResultHeaderType::getStatus)
      .flatMap(statusTypes -> statusTypes.stream().findFirst())
      .map(StatusBasisType::getCode)
      .map(CodeType::getValue);


    return Optional.ofNullable(bulkResultType)
      .flatMap(obm -> obm.getObservationBulkmailResult().stream().findFirst())
      .map(obmResult -> BulkResult.builder()
        .consignmentId(getConsignmentId(obmResult.getConsignmentID()))
        .documentDateTime(documentDateTime)
        .reportedDateTime(reportedDateTime)
        .lastModificationDateTime(lastModificationDateTime)
        .customerOrderId(Optional.ofNullable(obmResult.getCustomerOrderID()).map(IdentifierType::getValue))
        .orderLineNumber(Optional.ofNullable(obmResult.getOrderLineNumber()).map(IdentifierType::getValue))
        .startDateTime(Optional.ofNullable(obmResult.getResultTime()).map(TimePeriodABIEType::getStartDateTime))
        .endDateTime(Optional.ofNullable(obmResult.getResultTime()).map(TimePeriodABIEType::getEndDateTime))
        .statusCode(statusCode)
        .build());

  }

  private static Object getConsignmentId(Object consignmentID) {
    return null;
  }


  private static class Value { }

  private static class CodeType {
    String getValue() {
      return null;
    }
  }

  private static class IdentifierType {
    public String getValue() {
      return null;
    }
  }

  private static class InvalidMessageException extends RuntimeException {
    private InvalidMessageException(String s) {
    }
  }

  private static class BulkResult {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      public Builder consignmentId(Object consignmentId) {
        return null;
      }

      public Builder documentDateTime(LocalDateTime documentDateTime) {
        return null;
      }

      public Builder reportedDateTime(Optional<LocalDateTime> reportedDateTime) {
        return null;
      }

      public Builder lastModificationDateTime(Optional<LocalDateTime> lastModificationDateTime) {
        return null;
      }

      public Builder customerOrderId(Optional<String> s) {
        return null;
      }

      public Builder orderLineNumber(Optional<String> s) {
        return null;
      }

      public Builder startDateTime(Optional<LocalDateTime> localDateTime) {
        return null;
      }

      public Builder endDateTime(Optional<LocalDateTime> localDateTime) {
        return null;
      }

      public Builder statusCode(Optional<String> statusCode) {
        return null;
      }

      public BulkResult build() {
        return null;
      }
    }
  }

  public static class bulkResultHeaderType {
    public static ResultReleaseTimeType getResultReleaseTime(Object o) {
      return new ResultReleaseTimeType();
    }

    public static List<StatusBasisType> getStatus(bulkResultHeaderType observationBulkmailHeaderType) {
      return null;
    }
  }

  public static class BulkResultResult {
    public IdentifierType getCustomerOrderID() {
      return null;
    }

    public IdentifierType getOrderLineNumber() {
      return null;
    }

    public TimePeriodABIEType getResultTime() {
      return null;
    }

    public Object getConsignmentID() {
      return null;
    }
  }

  public static class BulkResultType {
    public static bulkResultHeaderType getObservationBulkmailHeader(BulkResultType bulkResultType) {
      return new bulkResultHeaderType();
    }

    public Collection<BulkResultResult> getObservationBulkmailResult() {
      return null;
    }
  }

  public static class ResultReleaseTimeType {
    public static LocalDateTime getReportedDateTime(Object o) {
      return null;
    }
  }

  public static class StatusBasisType {
    public static CodeType getCode(Object o) {
      return null;
    }
  }

  public static class TimePeriodABIEType {
    public LocalDateTime getStartDateTime() {return null;}
    public LocalDateTime getEndDateTime() {return null;}
  }

  public static class HeaderType {
    public static LocalDateTime getLastModificationDateTime(bulkResultHeaderType observationBulkmailHeaderType) {
      return null;
    }

    public static LocalDateTime getDocumentDateTime(bulkResultHeaderType observationBulkmailHeaderType) {
      return null;
    }
  }

  public static class LocalDateTime {}
}
