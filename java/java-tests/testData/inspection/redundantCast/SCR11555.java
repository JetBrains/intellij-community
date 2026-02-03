
class Test {
  void foo(){
    Component c = null;
    ((Frame) c).show();
  }
}

class Component {}
class Window extends Component {
  void show() {}
}
class Frame extends Window {}