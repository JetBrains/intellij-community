class IfWithDefaultNotNull {

  static String formatter(Object o) {
    String formatted = switch (o) {
        case Integer i -> String.format("int %d", i);
        case Long l -> String.format("long %d", l);
        case Double d -> String.format("double %f", d);
        case String s -> String.format("String %s", s);
        default -> o.toString();
    };
      <caret>return formatted;
  }
}