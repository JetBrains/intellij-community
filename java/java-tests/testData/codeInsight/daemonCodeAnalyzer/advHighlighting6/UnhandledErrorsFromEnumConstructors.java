
enum ABC {
  <error descr="Unhandled exception: java.io.IOException">A</error>(),
  <error descr="Unhandled exception: java.io.IOException">B</error>,
  <error descr="Unhandled exception: java.io.IOException">C</error>;
  ABC() throws java.io.IOException {
  }
}