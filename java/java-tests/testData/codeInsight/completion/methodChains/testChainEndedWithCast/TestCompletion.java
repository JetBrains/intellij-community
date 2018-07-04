interface InspectionManager {
  static InspectionManager getInstance() {
    return null;
  }
}
interface InspectionManagerEx extends InspectionManager {
}

class Test {
  void m() {
    InspectionManagerEx m1 = <caret>
  }
}