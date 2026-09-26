void main(String[] args) throws IOException {
  List.of("1").forEach(this::<caret>accept);
}

private record Person(String name) {
  @Override
  public String toString() {
    return "Person{" +
           "name='" + name + '\'' +
           '}';
  }
}

private void accept(String line) {
  Person person = new Person(line);
  IO.println(person);
}
