record FullName(String firstName, String lastName) {
  FullName {
    if (firstName.isEmpty() || lastName.isEmpty()) throw new IllegalArgumentException();
  }
}