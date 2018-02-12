interface InspectionManager {
  static InspectionManager getInstance() {
    return null;
  }
}
interface InspectionManagerEx extends InspectionManager {
  GlobalInspectionContext createContext();
}
interface GlobalInspectionContext {}

class Test {
  void m() {
    GlobalInspectionContext c = <caret>
  }
}