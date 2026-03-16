static void main(String[] args) throws IOException {
  List.of("1").forEach(line -> {
    Person person = new Person(line);
    IO.println(person);
  <caret>});
}

private record Person(String name) {
  @Override
  public String toString() {
    return "Person{" +
           "name='" + name + '\'' +
           '}';
  }
}
