enum C {
  ORIGINAL_CODE_WITH_COMMENT, THE_SAME_CODE_WITH_DIFFERENT_COMMENT,COMMENT_INSIDE_THE_CODE,LINE_COMMENT,
  JAVADOC_COMMENT,COMMENT_WITH_NEW_LINES,EMPTY_COMMENTS_ARE_IGNORED,
  COMMENT_RIGHT_BEFORE_A_CASE_IS_ATTACHED_TO_THAT_CASE;

  String foo(C c) {
    switch (c) {
      case ORIGINAL_CODE_WITH_COMMENT:
        /* comment 1 */
        return "A";
      case THE_SAME_CODE_WITH_DIFFERENT_COMMENT:
        /* comment 2 */
        return "A";
      case LINE_COMMENT:
        // comment 1
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
      case COMMENT_INSIDE_THE_CODE:
        <weak_warning descr="Duplicate branch in 'switch' statement">return /* comment 1 */"A";</weak_warning>
      case JAVADOC_COMMENT:
        /** comment 1 */
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
      case COMMENT_WITH_NEW_LINES:
        /*
        comment 1
        */
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
      case EMPTY_COMMENTS_ARE_IGNORED:
        /* comment 1 */
        //
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
      // comment 1
      case COMMENT_RIGHT_BEFORE_A_CASE_IS_ATTACHED_TO_THAT_CASE:
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
    }
    return "";
  }
}