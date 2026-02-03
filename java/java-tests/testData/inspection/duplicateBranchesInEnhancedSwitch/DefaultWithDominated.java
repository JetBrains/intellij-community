class C {
  public Object convert(Object value) {
    switch (value) {
      case Integer ignored -> {
        return value;
      }
      case Number v -> {
        return v.toString();
      }
      default -> {
        return value;
      }
    }
  }

  public Object convert2(Object value) {
    switch (value) {
      case Integer ignored -> <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">{
        return value;
      }</weak_warning>
      case String v -> {
        return v + "1";
      }
      default -> {
        return value;
      }
    }
  }

  public Object convert3(Object value) {
    switch (value) {
      case String v -> {
        return v + "1";
      }
      case Integer ignored -> <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">{
        return value;
      }</weak_warning>
      default -> {
        return value;
      }
    }
  }
}