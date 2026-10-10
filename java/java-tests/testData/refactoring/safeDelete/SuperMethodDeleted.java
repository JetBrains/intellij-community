class Base {
  void run() {}
}

class Impl extends Base {
  @Override
  void <caret>run() {}
}
