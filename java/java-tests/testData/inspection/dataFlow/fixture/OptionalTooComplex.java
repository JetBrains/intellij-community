import java.util.Optional;

// IDEA-184723
class OptionalTooComplex {
  // Should not be too complex
  public Long fetch() {
    final PreparedStatement ps = builder
      .setInteger(<warning descr="Argument 'opt().orElse(null)' might be null but passed to non annotated parameter">opt().orElse(null)</warning>)
      .setInteger(<warning descr="Argument 'opt().orElse(null)' might be null but passed to non annotated parameter">opt().orElse(null)</warning>)
      .setInteger(<warning descr="Argument 'opt().orElse(null)' might be null but passed to non annotated parameter">opt().orElse(null)</warning>)
      .setInteger(<warning descr="Argument 'opt().orElse(null)' might be null but passed to non annotated parameter">opt().orElse(null)</warning>)
      .setInteger(<warning descr="Argument 'opt().orElse(null)' might be null but passed to non annotated parameter">opt().orElse(null)</warning>)
      .build();

    ResultSet rs = null;
    try {
      rs = ps.executeQuery();
      if (rs.next()) {
        return rs.getLong();
      }
    } catch (final Exception e) {
      throw new RuntimeException(e);
    } finally {
    }

    return 0L;
  }

  interface ResultSet {
    boolean next() throws Exception;

    long getLong() throws Exception;
  }

  interface PreparedStatement {
    ResultSet executeQuery() throws Exception;
  }

  native Optional<Integer> opt();

  interface QueryBuilder {
    QueryBuilder setInteger(Integer value);

    PreparedStatement build();
  }

  QueryBuilder builder;
}
