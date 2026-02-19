// IDEA-360579
class Hades {
  void persephone(boolean xyz, int param) {
    if(xyz && !in<caret>lineMe(param)){
      System.out.println();
    }
  }

  boolean inlineMe(int param) {
    boolean result = false;
    if (param >= 10) {
      result = param != 20;
    }
    return result;
  }
}