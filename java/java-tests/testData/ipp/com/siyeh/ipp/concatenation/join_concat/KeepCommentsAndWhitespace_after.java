class KeepCommentsAndWhitespace {
  static {
      //same line comment
      System.out.println("select foo_id, bar, baz " +
              "from foo f " +
              "where bar=1 " +
              "  and baz=2   and gazonk < (" + // comment
              "        select count(distinct feeble) " +
              "        from dribble " +
              "        where zabble = f.bar)");
  }
}