public class Aaaaaaa {
  void foo(ActionEvent e) {
    bar(<caret>);
  }

  void bar(ActionEvent event) {

  }

}

class ActionEvent {
  static ActionEvent createEvent() {

  }
}