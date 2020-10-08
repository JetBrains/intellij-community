// "Convert to a record" "false"
class <caret>Service1 {
  final Service2 service2;

  final Service1(Service2 service2) {
    this.service2 = service2;
  }
}

class Service2 {
}
