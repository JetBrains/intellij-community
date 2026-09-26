enum EnumConstantArgumentClassNames {
  FIRST(new Object() { }) {
    Object member = new Object() { };
  },
  SECOND(null) { };

  EnumConstantArgumentClassNames(Object value) { }
}
