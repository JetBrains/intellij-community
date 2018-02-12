interface InspectionManager {
  static InspectionManager getInstance() {
    return null;
  }
}
interface InspectionManagerEx extends InspectionManager {
}

class Test {
  void m() {
    InspectionManagerEx m1 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m2 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m3 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m4 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m5 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m6 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m7 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m8 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m9 = (InspectionManagerEx)InspectionManager.getInstance();
    InspectionManagerEx m0 = (InspectionManagerEx)InspectionManager.getInstance();
  }
}